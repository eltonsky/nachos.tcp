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
    		
    		Machine.processor().writeTLBEntry(i, null);
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
    		
    		if(tlbStore[i] != null){
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
		if (numPages > Machine.processor().getNumPhysPages() ||
				numPages > UserKernel.getFreePageNum()) {
		    coff.close();
		    Lib.debug(dbgProcess, "\tinsufficient physical memory or free memory.");
		    return false;
		}
		
		Lib.debug(dbgProcess, "numPages is " + numPages);
		
		// args : 1 page, only this page is phy allocated.
		pageTable.put(numPages-1,new TranslationEntry(numPages-1, UserKernel.allocatePage(), true,false,false,false));
		
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
    	int rs = super.readVirtualMemory(vaddr,data,offset,length);
    	
    	int firstVpn = Processor.pageFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
		TLB tlb = VMKernel.getTLB();
		
    	//update TLB
    	for(int i = firstVpn; i<=lastVpn;i++){
	    	TranslationEntry te = super.pageTable.get(i);
	
	    	te.used = true;
	    	
	    	VMKernel.getTLBLock().acquire();
	    	
	    	tlb.set(this.getPID(), i);
	    	
	    	VMKernel.getTLBLock().release();
    	}
    	
    	return rs;
    }
    
    
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
			  int length) {
    	int rs = super.writeVirtualMemory(vaddr, data, offset,length);
    	
    	int firstVpn = Processor.pageFromAddress(vaddr);
		int lastVpn = Processor.pageFromAddress(vaddr+length);
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
    	
    	return rs;
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
    
    public void handleTLBMiss(int vaddr) {
    	Lib.assertTrue(pageTable != null);
    	
    	int vpn = Processor.pageFromAddress(vaddr);
    	
    	// if page is not available, get it in
    	if (!pageTable.containsKey(vpn) || !pageTable.get(vpn).valid) {
    		
    		VMKernel.getPageLock().acquire();
    		
    		load_page(vpn);
    		
    		VMKernel.getPageLock().release();
    	}
    	
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
    	IPTPage pages = VMKernel.getIPTPage();
    	SwapFile swap = VMKernel.getSwap();
    	
    	// if no more free page, swap! 
    	if (VMKernel.getFreePageNum() == 0) {
    		
    		int outVpn = Integer.parseInt(pages.evict().split(":")[1]);
    		
        	// if page to swap out is dirty, write to swap    		
    		if(pageTable.get(outVpn).dirty) {    			
    			swap.writeToSwap(this.getPID(), 
    					pageTable.get(outVpn).vpn, pageTable.get(outVpn).ppn);
    		}
    		
    		pageTable.remove(outVpn);
    		pages.remove(this.getPID()+":"+outVpn);
    	}
    	
    	
    	// load target page
    	// if in swap (.data, stack, malloc)
    	if(swap.contains(this.getPID(), vpn)){
    		pageTable.put(vpn,new TranslationEntry(vpn, UserKernel.allocatePage(), 
    				true, false,false,false));
    		
    		swap.readFromSwap(this.getPID(), vpn, pageTable.get(vpn).ppn);
    	}
    	// if this stack page doesn't exist yet, create it.
    	else if(isStackPage(vpn)){
    		pageTable.put(vpn,new TranslationEntry(vpn, UserKernel.allocatePage(), 
    				true, false,false,false));
        }
    	// if code file (.text, .rdata, .data or .bss), load from coff 
    	else if(lazyLoader.isInCoff(vpn)){
    		pageTable.put(vpn,new TranslationEntry(vpn, UserKernel.allocatePage(), 
    				true, lazyLoader.isReadOnly(vpn),false,false));
    		
    		lazyLoader.loadSection(vpn,pageTable.get(vpn).ppn);    	
    		
    	} else {
    		Lib.assertNotReached("Can not find page, neither in coff OR swap OR stack. pid " 
    			+ this.getPID() + " vpn " + vpn);
    	} 

		pages.set(this.getPID(), vpn, pageTable.get(vpn));
    	
    	return 0;
    }
    
    
    // if this page is a stack page.
    private boolean isStackPage(int vpn) {
    	int last = this.initialSP/Processor.pageSize - 1;
    	int first = last - this.stackPages;
    	
    	return (vpn >= first && vpn <= last);
    }
    
    
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
    
}
