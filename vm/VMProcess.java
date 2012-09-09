package nachos.vm;
import nachos.machine.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
		super();
		
		super.useLazyLoader = true;
		
		for(int i=0; i < Machine.processor().getTLBSize();i++)
			tlbStore[i] = new TranslationEntry();
		
		VMKernel.getCoreMap().insert(this.getPID(), pageTable);
    }
    
    
    private TranslationEntry[] tlbStore = new TranslationEntry[Machine.processor().getTLBSize()];
    
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    	super.saveState();
    	
    	for(int i=0; i < Machine.processor().getTLBSize(); i++) {
    		tlbStore[i] = Machine.processor().readTLBEntry(i);
    		
    		Machine.processor().writeTLBEntry(i, new TranslationEntry());
    	}
    	
    	// invalidate policy records.
    	VMKernel.getTLB().invalidate();
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() { 
    	for(int i=0; i < Machine.processor().getTLBSize(); i++) {    		
    		Machine.processor().writeTLBEntry(i, tlbStore[i]);
    		
    		if(tlbStore[i].valid){
    			// insert into policy records.
    			VMKernel.getTLB().set(this.getPID(), tlbStore[i].vpn);
    		}
    	}
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
		Lib.debug(dbgProcess, "numPages is " + numPages);
		
		// args : 1 page, only this page is phy allocated.
		VMKernel.getPageLock().acquire();
		
		//NOT:cheeky! set dirty, so it will be write to swap whenever swapped out.
		pageTable.put(numPages-1,new TranslationEntry(numPages-1, 
				VMKernel.allocatePage(this.getPID()), true,false,false,true,false));
		
		VMKernel.getIPTPage().set(this.getPID(), numPages-1, pageTable.get(numPages-1));
		
		VMKernel.getPageLock().release();
		
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	super.unloadSections();
    }    

    public int readVirtualMemory(int vaddr, byte[] data, int offset,
			 int length) {
    	
    	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
    	
    	int leftLength = length;
    	
		int firstVpn = Processor.pageFromAddress(vaddr);
		int firstVpOffset = Processor.offsetFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
		
		Lib.debug(dbgProcess, "vaddr "+ vaddr + " offset " + offset + 
				" length " + length  +" firstVpn " + firstVpn + " firstVpOffset " + 
				firstVpOffset + " lastVpn " + lastVpn);
		
		byte[] memory = Machine.processor().getMemory();
		
		int paddr = -1;
		int thisRead = 0, totalRead = 0, destOffset = 0;
		
		for(int i = firstVpn; i<=lastVpn; i++){
			destOffset = offset + totalRead;
			
			if(i==firstVpn){
				paddr = getPhysicAddress(i,firstVpOffset);
				
				thisRead = Math.min(Processor.pageSize - firstVpOffset,length);				
			} else  {
				paddr = getPhysicAddress(i,0);
				
				if (i == lastVpn)
					thisRead = leftLength;
				else
					thisRead = Processor.pageSize;
			}

			totalRead += thisRead;
			leftLength -= thisRead;
			
			Lib.debug(dbgProcess, "paddr " + paddr +" destOffset " + destOffset + " thisRead " + thisRead);
			
			System.arraycopy(memory, paddr, data, destOffset, thisRead);			
		}
	
    	
		TLB tlb = VMKernel.getTLB();
		
    	//update TLB
    	for(int i = firstVpn; i<=lastVpn;i++){
	    	TranslationEntry te = super.pageTable.get(i);
	
	    	te.used = true;
	    	
	    	VMKernel.getTLBLock().acquire();
	    	
	    	tlb.set(this.getPID(), i);
	    	
	    	VMKernel.getTLBLock().release();
    	}
    	
    	return totalRead;
    }
    
    
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
			  int length) {
    	
    	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
    	
    	int leftLength = length;
    	
		int firstVpn = Processor.pageFromAddress(vaddr);
		int firstOffset = Processor.offsetFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
			
		Lib.debug(dbgProcess, "vaddr "+ vaddr + " offset " + 
				offset + " length " + length  +" firstVpn " + firstVpn + " firstOffset " + 
				firstOffset + " lastVpn " + lastVpn);
		
		byte[] memory = Machine.processor().getMemory();
		
		int paddr = -1, thisWrite = -1, dataOffset = 0, totalWrite = 0;
		
		for(int i = firstVpn; i <= lastVpn; i++){
			dataOffset += totalWrite;
			
			if(i == firstVpn){
				paddr = getPhysicAddress(i, firstOffset);
				thisWrite = Math.min(Processor.pageSize - firstOffset, length);
			} else {
				paddr = getPhysicAddress(i, 0);
				
				if(i == lastVpn) {
					thisWrite = leftLength;
				} else 
					thisWrite = Processor.pageSize;
			}
			
			Lib.debug(dbgProcess, "paddr " + paddr + " dataOffset "
					+dataOffset+" thisWrite " + thisWrite + " totalWrite " + totalWrite);
			
			System.arraycopy(data, dataOffset, memory, paddr, thisWrite);
			totalWrite += thisWrite;
			
			leftLength -= thisWrite;
		}
		
		
		TLB tlb = VMKernel.getTLB();
		
    	//update TLB
		for(int i = firstVpn; i<=lastVpn;i++){
	    	TranslationEntry te = super.pageTable.get(i);
	    	
	    	te.used = true;
	    	te.dirty = true;
	    	
	    	VMKernel.getTLBLock().acquire();
	    	
	    	tlb.set(this.getPID(), i);
	    	
	    	VMKernel.getTLBLock().release();
		}
    	
    	return totalWrite;
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
			
			case Processor.exceptionTLBMiss:
			    handleTLBMiss(Machine.processor().readRegister(Processor.regBadVAddr));
			    break;	
			    
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
			    super.handleException(cause);
			    break;
		}
    }
    
    
    protected int getPhysicAddress(int vpn, int offset){
    	Lib.debug(dbgVM, "vpn " + vpn + " offset " + offset);
    	
    	checkPage(vpn);
    	
    	int ppn = pageTable.get(vpn).ppn;
    	return ppn*Processor.pageSize + offset;
    }
    
    
    // if page is not available, get it in
    private void checkPage(int vpn) {
    	
    	printPageTable();
    	
    	if (!pageTable.containsKey(vpn) || !pageTable.get(vpn).valid) {
    		
    		VMKernel.getPageLock().acquire();
    		
    		load_page(vpn);
    		
    		VMKernel.getPageLock().release();
    	}
    }
    
    public void handleTLBMiss(int vaddr) {
    	Lib.assertTrue(pageTable != null);
    	
Lib.debug(dbgVM, "vaddr " + vaddr);    	
    	
    	int vpn = Processor.pageFromAddress(vaddr);
    	
	    checkPage(vpn);
    	
    	VMKernel.getTLBLock().acquire();
    	
    	int index = VMKernel.getTLB().set(this.getPID(), vpn);
    	
    	Machine.processor().writeTLBEntry(index, pageTable.get(vpn));
    	
    	VMKernel.getTLBLock().release();
    	
    	Lib.debug(dbgVM, "set vpn " + vpn);
    }
    
    
    protected int handleExec(int a_file, int argc, int a_argv){
        String filename = readVirtualMemoryString(a_file, 256);
    
        String[] argv = new String[argc];
        
        Lib.debug(dbgProcess, "filename " + filename + " argc " + argc + ", a_argv " + a_argv);        
        
        int vaddr = a_argv;
        String currArg;
        for(int i=0;i<argc;i++){
        	currArg = readVirtualMemoryString(vaddr, 64);
        	
        	argv[i] = currArg;
        	// assign 64 chars for each arg
        	vaddr += 64;
        	
        	Lib.debug(dbgProcess, "argv" +i +" "+ argv[i] +", vaddr " + vaddr);        	
        }
        
        printMemoryString(a_argv,1024);        
        
        if(filename == null || !filename.endsWith(".coff") || argc < 0){
                return -1;
        }else{
        	VMProcess childProcess = new VMProcess();
            childProcess.parentPID = this.getPID();
            childrenMap.put(childProcess.getPID(), childProcess);
            childProcess.execute(filename, argv);
            return childProcess.getPID();
        }
	}
    
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    	
    	Lib.debug(dbgProcess, "... Syscall is " + syscall);
    	
		switch (syscall) {
		case syscallHalt:
		    return handleHalt();
		case syscallExit:
			handleExit(a0);
			break;
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
		case syscallJoin:
			return handleJoin(a0,a1);
		case syscallMalloc:
			return handleMalloc(a0);
		case syscallFree:
			handleFree(a0);
			break;
			
		default:
		    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
		    Lib.assertNotReached("Unknown system call!");
		}
		return 0;
    }
    
    
    // load from coff OR swap
    protected int load_page(int vpn) {
    	
    	Lib.debug(dbgVM, "&&load vpn " +vpn);    	
    	
    	if(lazyLoader==null)
    		lazyLoader = new LazyLoader(coff);
    	
    	IPTPage pages = VMKernel.getIPTPage();
    	SwapFile swap = VMKernel.getSwap();	
    	
    	// load target page
    	// if in swap (.data, stack, malloc)
    	if(swap.contains(this.getPID(), vpn)){    		
    		pageTable.put(vpn,new TranslationEntry(vpn, VMKernel.allocatePage(this.getPID()), 
    				true, false,false,false,false));
    		
    		swap.readFromSwap(this.getPID(), vpn, pageTable.get(vpn).ppn);
    		
    		Lib.debug(dbgVM, "&&In swap, vpn " + vpn + " ppn " + pageTable.get(vpn).ppn);    		
    	}
    	// if this stack page doesn't exist yet, create it.
    	else if(isStackPage(vpn)){    		
    		pageTable.put(vpn,new TranslationEntry(vpn, VMKernel.allocatePage(this.getPID()), 
    				true, false,false,false,false));
    		
    		pages.currentStackKey = this.getPID()+":"+vpn;
    		
    		Lib.debug(dbgVM, "&&Is stack, vpn " + vpn + " ppn " + pageTable.get(vpn).ppn);
        }
    	// if code file (.text, .rdata, .data or .bss), load from coff 
    	else if(lazyLoader.isInCoff(vpn)){
    		pageTable.put(vpn,new TranslationEntry(vpn, VMKernel.allocatePage(this.getPID()), 
    				true, lazyLoader.isReadOnly(vpn),false,false, lazyLoader.isExecutable(vpn)));
    		
    		lazyLoader.loadSection(vpn,pageTable.get(vpn).ppn);
    		
    		if(lazyLoader.isExecutable(vpn))
    			pages.currentTextKey = this.getPID()+":"+vpn;
    		
    		Lib.debug(dbgVM, "&&Is Coff, vpn " + vpn + " ppn " + pageTable.get(vpn).ppn);    		
    	} else {
    		Lib.assertNotReached("Can not find page, neither in coff OR swap OR stack. pid " 
    			+ this.getPID() + " vpn " + vpn);
    	} 

		pages.set(this.getPID(), vpn, pageTable.get(vpn));
    	
    	return 0;
    }
    
    
    // if this page is a stack page.
    private boolean isStackPage(int vpn) {
    	int last = this.initialSP/Processor.pageSize;
    	int first = last - this.stackPages;
    	
    	return (vpn >= first && vpn <= last);
    }
    
    
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
    protected LazyLoader lazyLoader;
    
}
