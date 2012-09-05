package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
    	super();

	    tlb = new TLB("InvertedPageTable","RandomReplace");
    	
    	swap = SwapFile.getSwapFile();
    	
		tlbLock = new Lock();
		
		pageLock = new Lock();

		pages =  new IPTPage();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
	super.initialize(args);
    }

    /**
     * Test this kernel.
     */	
    public void selfTest() {
	super.selfTest();
    }

    /**
     * Start running user programs.
     */
    public void run() {
	super.run();
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
    	swap.purge();
    	
    	pages.clear();
    	
    	super.terminate();
    }
    
    public static SwapFile getSwap(){
    	return swap;
    }
    
    public static TLB getTLB(){
    	return tlb;
    }
    
    public static Lock getTLBLock() {
    	return tlbLock;
    }

    public static Lock getPageLock() {
    	return pageLock;
    }
    
    public static IPTPage getIPTPage(){
    	return pages;
    }
    
    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
    
    private static SwapFile swap;

    private static TLB tlb;

	private static IReplacePolicy pageReplacePolicy;
    
	private static Lock tlbLock;
	private static Lock pageLock;
	
	private static IPTPage pages;
}
