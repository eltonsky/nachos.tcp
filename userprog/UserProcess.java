package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	
    	// move the the definition of pageTalbe to loadSection();
    	// coz numofPages is only available until then; and as heap is not considered,
    	// numOfPage is static.
    	
		//pid
		setProcessID();		
		
		//fd table
		fd_table = new FDTable();
		//add stdin, stdout
		fd_table.addFile(UserKernel.console.openForReading());
		fd_table.addFile(UserKernel.console.openForWriting());
		
		//insert into processMap
		processMap.put(this.getPID(), this);
    }
    
    
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
    	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
		if (!load(name, args))
		    return false;
		
		UThread userthread = new UThread(this);
		this.userthread = userthread;
		userthread.setName(name).fork();
	
		return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
    	Machine.processor().setPageTable(pageTable);
    }

    public void printMemoryString(int a0) {
		byte[] bytes = new byte[1024];
	
		int bytesRead = readVirtualMemory(a0, bytes);
	
		Lib.debug(dbgProcess, "Machine Mem :" + new String(bytes,0,bytesRead));
    }
    
    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0){
	    
	    	Lib.debug(dbgProcess, "##elton## " + new String(bytes, 0, length));
	    	
	    	return new String(bytes, 0, length);
	    }
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    
    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
	
		int firstVpn = Processor.pageFromAddress(vaddr);
		int firstVpOffset = Processor.offsetFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
		int lastVpReadLength = 0; 
		if (lastVpn <= numPages)
			lastVpReadLength = Processor.offsetFromAddress(vaddr+length);
		else {
			lastVpn = numPages -1;
			lastVpReadLength = 0;
		}
		
		Lib.debug(dbgProcess, "##elton## vaddr "+ vaddr + " offset " + offset + 
				" length " + length  +" firstVpn " + firstVpn + " firstVpOffset " + 
				firstVpOffset + " lastVpn " + lastVpn + " lastVpReadLength " + lastVpReadLength);
		
		byte[] memory = Machine.processor().getMemory();
		
		int paddr = -1;
		int thisRead = 0, totalRead = 0, destOffset = 0;
		
		for(int i = firstVpn; i<=lastVpn; i++){
			destOffset = offset + totalRead;
			
			if(i==firstVpn){
				paddr = getPhysicAddress(i,firstVpOffset);
				
				thisRead = Math.min(pageSize - firstVpOffset,length);				
			} else  {
				paddr = getPhysicAddress(i,0);
				
				if (i == lastVpn)
					thisRead = lastVpReadLength;
				else
					thisRead = pageSize;
			}
			
			Lib.debug(dbgProcess, "##elton## paddr " + paddr + " destOffset "
					+destOffset+" thisRead " + thisRead + " totalRead " + totalRead);
			
			System.arraycopy(memory, paddr, data, destOffset, thisRead);
			
			totalRead += thisRead;
		}
	
		return totalRead;
    }

    
    /** 
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
    	
    	Lib.debug(dbgProcess, "##elton## " + new String(data, 0, data.length));
    	
    	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
	
		int firstVpn = Processor.pageFromAddress(vaddr);
		int firstOffset = Processor.offsetFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
		int lastWriteLength = -1;
		if(lastVpn < numPages)
			lastWriteLength= Processor.offsetFromAddress(vaddr+length);
		else{
			lastWriteLength= pageSize;
			lastVpn = numPages - 1;
		}
			
		Lib.debug(dbgProcess, "##elton## vaddr "+ vaddr + " offset " + 
				offset + " length " + length  +" firstVpn " + firstVpn + " firstOffset " + 
				firstOffset + " lastVpn " + lastVpn + " lastWriteLength " + lastWriteLength);
		
		byte[] memory = Machine.processor().getMemory();
		
		int paddr = -1, thisWrite = -1, dataOffset = 0, totalWrite = 0;
		
		for(int i = firstVpn; i <= lastVpn; i++){
			dataOffset += totalWrite;
			
			if(i == firstVpn){
				paddr = getPhysicAddress(i, firstOffset);
				thisWrite = Math.min(pageSize - firstOffset, length);
			} else {
				paddr = getPhysicAddress(i, 0);
				
				if(i == lastVpn) {
					thisWrite = lastWriteLength;
				} else 
					thisWrite = pageSize;
			}
			
			Lib.debug(dbgProcess, "##elton## paddr " + paddr + " dataOffset "
					+dataOffset+" thisWrite " + thisWrite + " totalWrite " + totalWrite);
			
			System.arraycopy(data, dataOffset, memory, paddr, thisWrite);
			totalWrite += thisWrite;
		}
	
		return totalWrite;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    if (section.getFirstVPN() != numPages) {
			coff.close();
			Lib.debug(dbgProcess, "\tfragmented executable");
			return false;
	    }
	    
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	
	for (int i=0; i<args.length; i++) {
		
Lib.debug(dbgProcess, "args " + i +": " + args[i]);
		
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	
Lib.debug(dbgProcess, "argsSize is " + argsSize);	
	
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
Lib.debug(dbgProcess, "argv length is " + argv.length);
	
	for (int i=0; i<argv.length; i++) {	
		
Lib.debug(dbgProcess, "argv[" + i +"] is " + argv[i] + " String: " + new String(argv[i]));
		
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
		    coff.close();
		    Lib.debug(dbgProcess, "\tinsufficient physical memory");
		    return false;
		}
	
		if (numPages > UserKernel.getFreePageNum()) {
            coff.close();
		    Lib.debug(dbgProcess, "\t Not enough free physical memory");
		    return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		
		
		int vpn = 0;
		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
		    CoffSection section = coff.getSection(s);
		    
		    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			      + " section (" + section.getLength() + " pages)");
	
		    for (int i=0; i<section.getLength(); i++) {
				vpn = section.getFirstVPN()+i;
		
				// allocate mem
				// readonly is set to true, if it is from .text
				pageTable[vpn] = new TranslationEntry(vpn, UserKernel.allocatePage(), true,section.isReadOnly(),false,false);
				
				section.loadPage(i, pageTable[vpn].ppn);
		    }
		}
		
		// if not allocated to numPages for section, allocate more here.
		for(int j = vpn+1; j < numPages; j++){
			pageTable[j] = new TranslationEntry(j, UserKernel.allocatePage(), true,false,false,false);
		}
		
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     * free pages
     * close coff
     */
    protected void unloadSections() {
    	for(TranslationEntry te : pageTable){
    		UserKernel.freePage(te.ppn);
    	}
    	
    	coff.close();
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
		Processor processor = Machine.processor();
	
		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
		    processor.writeRegister(i, 0);
	
		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);
	
		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

    	// only main process is allowed to call halt.
    	if(pid != 0){
    		Lib.debug('t', "current pid " + pid + " is not allowed to call halt().");
    		return 0;
    	}
    	
		Machine.halt();
		
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
    }

    /**
     * Open a new file, creating it if it does not exist.
     * @param a0 the virtual memory address of the filename string.
     * @return the new file descriptor on success, or -1 on failure.
     */
    private int handleCreate(int a0){
    	//printMemoryString(a0);
    	
        String filename = readVirtualMemoryString(a0, 256);
        
        Lib.debug(dbgProcess, "filename is " + filename + " a0 is " + a0);
        
        if(filename == null){
        	return -1;
        }else{
            OpenFile createdFile = UserKernel.fileSystem.open(filename, true);
            if(createdFile == null){
            	return -1;
            }

            int fd = fd_table.addFile(createdFile);
            
            Lib.debug(dbgProcess, "fd " + fd);
            
            return fd;
        }
    }
    
    /**
     * Opens a file at a given location in the file system, only if
     * the file is already existing.
     * @param a0 a memory address of a string containing the path to
     * the file to open.
     * @return the file descriptor of the newly opened file, or -1 if
     * an error occurred.
     */
    protected int handleOpen(int a0){
	    String filename = readVirtualMemoryString(a0, 256);
	    if(filename == null){
	    	return -1;
	    }else{
            OpenFile openedFile = UserKernel.fileSystem.open(filename, false);
            if(openedFile == null){
            	return -1;
            }

            int fd = fd_table.addFile(openedFile);
            return fd;
	    }
    }
    
    /**
     * Reads data from a file descriptor into a buffer until a given
     * amount of bytes have been read.
     * @param a0 the file descriptor to read from.
     * @param a1 a memory address of a buffer to write the data to.
     * @param a2 the maximum number of bytes to read from the descriptor.
     * @return the number of bytes read from the descriptor, or -1 if an error
     * occurred.
     */
    protected int handleRead(int a0, int a1, int a2){
        if(a0 >= 0 && fd_table.getFile(a0) != null
        		&& a1 >=0 && a2 > 0){
            OpenFile file = fd_table.getFile(a0);

            byte[] data = new byte[a2];
            int bytesRead = file.read(data, 0, a2);

            if(bytesRead < 0){
                return -1;
            }else if(bytesRead == 0){
                return 0;
            }

            byte[] dataToWrite = new byte[bytesRead];
            System.arraycopy(data, 0, dataToWrite, 0, bytesRead);

            int bytesWritten = writeVirtualMemory(a1, dataToWrite);

            if(bytesWritten < 0){
                return -1;
            }

            return bytesRead;
        }else{
        	Lib.debug(dbgProcess, "a0 "+ a0 +", a1" + a1 + ", a2 "+ a2 + "");
        	
            return -1;
        }
    }
    
    
    /**
     * Writes data from a file descriptor into a buffer until a given
     * amount of bytes have been written.
     * @param a0 the file descriptor to write to.
     * @param a1 a memory address of a buffer to read data from.
     * @param a2 the maximum number of bytes to write to the descriptor.
     * @return the number of bytes write to the descriptor, or -1 if an error
     * occurred.
     */
    protected int handleWrite(int a0, int a1, int a2){
    	Lib.debug(dbgProcess, "a0 " + a0 + " a1 " +a1+" a2 " + a2 + " fd_table.getFile(a0) " + fd_table.getFile(a0));
    	
        if(a0 >= 0 && fd_table.getFile(a0) != null
        		&& a1 >=0 && a2 > 0){
        	
        	byte[] dataToRead = new byte[a2];
        	int bytesRead = readVirtualMemory(a1,dataToRead);
        	
        	if(bytesRead < 0){
                return -1;
            }else if(bytesRead == 0){
                return 0;
            }
        	
            OpenFile file = fd_table.getFile(a0);

            int bytesWritten = file.write(dataToRead, 0, bytesRead);

            if(bytesWritten < 0){
                return -1;
            }

            return bytesWritten;
        }else{
            return -1;
        }
    }    
    
    /**
     * Closes an open file descriptor. Close will return an error if
     * there is no open file descriptor at the given index.
     * @param a0 the file descriptor to close
     * @return 0 on success, -1 on failure.
     */
    protected int handleClose(int a0){
    	Lib.debug(dbgProcess, "a0 " + a0);
    	
        if(a0 < fd_table.size() && a0 >= 0 && fd_table.getFile(a0) != null){
        	fd_table.getFile(a0).close();
        	fd_table.deleteFile(a0);
            return 0;
        }

        return -1;
    }
    
    /**
     * remove a hard link for specified file name.
     * @param a0
     * @return
     */
    private int handleUnlink(int a0){
        String filename = readVirtualMemoryString(a0, 256);
        if(filename == null){
                return -1;
        }else{
            if(!UserKernel.fileSystem.remove(filename)){
                    return -1;
            }else{
                    return 0;
            }
        }
	}
    
    /*
     * 
     */
    private int handleExec(int a_file, int argc, int a_argv){
        String filename = readVirtualMemoryString(a_file, 256);
        byte[] b = new byte[256];
        
        String[] argv = new String[argc];
        
        int vaddr = a_argv;
        String currArg;
        for(int i=0;i<argc;i++){
        	currArg = readVirtualMemoryString(vaddr, 256);
        	
        	argv[i] = currArg;
        	vaddr += currArg.length();
        }
        
        if(filename == null || !filename.endsWith(".coff") || argc < 0){
                return -1;
        }else{
            UserProcess childProcess = new UserProcess();
            parentPID = this.getPID();
            childProcess.execute(filename, argv);
            
            return childProcess.getPID();
        }
	}

    /*
     * 
     */
    private int handleJoin(int childPID, int status) {
    	UserProcess child = processMap.get(childPID);
    	// only parent is allowed to join this process
    	if(child.parentPID != this.getPID())
    		return -1;
    	
    	child.userThread.join();
    	
    	
    	
    	return 0;
    }
    
    /*
     * 
     */
    private int handleExit() {
    	return 0;
    }

    private static final int
        syscallHalt = 0,
		syscallExit = 1,
		syscallExec = 2,
		syscallJoin = 3,
		syscallCreate = 4,
		syscallOpen = 5,
		syscallRead = 6,
		syscallWrite = 7,
		syscallClose = 8,
		syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    	
    	Lib.debug(dbgProcess, "Enter ... syacall is " + syscall);
    	
		switch (syscall) {
		case syscallHalt:
		    return handleHalt();
		case syscallExit:
			return handleExit();
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
	
			
		default:
		    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
		    Lib.assertNotReached("Unknown system call!");
		}
		return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    
    private int getPhysicAddress(int vpn, int offset){
    	if(pageTable.length < vpn+1)
    		return -1;
    				
    	int ppn = pageTable[vpn].ppn;
    	return ppn*pageSize + offset;
    }
    
    
    // assign new process id
    private void setProcessID(){
		pidLock.acquire();
		pid = currentPID;
		currentPID = (currentPID + 1)%MAX_PROCESS_NUM;
		pidLock.release();
    }
    
    public int getPID() {
    	return pid;
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private FDTable fd_table;
    private int initialPC, initialSP;
    private int argc, argv;
    private int pid;
    private int parentPID;
    private UThread userThread;
    
    private static final int MAX_PROCESS_NUM = 32768;
    private static final int maxOpenFiles = 16;
    private static final int numVMPages = 8;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static HashMap<Integer,UserProcess> processMap = new HashMap<Integer,UserProcess>();
    
    public static int currentPID = 0;
    public static int runningProcesses = 0;
    public static Lock pidLock = new Lock();
    
    private class FDTable {
    	OpenFile[] fdFile = new OpenFile[maxOpenFiles];
    	int lastIndex = 0;
    	int length = 0;
    	
    	public int addFile(OpenFile file){    		
    		int newIndex = getAvailableIndex();
    		if(newIndex == - 1)
    			return -1;
    		
    		fdFile[newIndex] = file;
    	
    		length++;
    		
    		return newIndex;
    	}
    	
    	public int deleteFile(int fd){
    		if(fdFile[fd] == null)
    			return -1;
    		
    		fdFile[fd] = null;
    		length--;
    		
    		return 0;
    	}
    	
    	public OpenFile getFile(int fd){    		
    		return fdFile[fd];
    	}
    	
    	public int size(){
    		return length;
    	}
    	
    	private int getAvailableIndex() {
    		for(int i = 0; i < maxOpenFiles; i++){
    			if(fdFile[(lastIndex+i)%maxOpenFiles] == null){
    				lastIndex = (lastIndex+i)%maxOpenFiles;	
    				return lastIndex;
    			}
    		}
    		
    		return -1;
    	}
    }
  
}
