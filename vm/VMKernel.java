package nachos.vm;

import java.util.TreeMap;

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
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
    	super.initialize(args);

    	InvertedPageTable ipt = new InvertedPageTable(Machine.processor().getTLBSize());
    	RandomReplace rr = new RandomReplace(Machine.processor().getTLBSize());
    	ipt.setPolicy(rr);
	    tlb = new TLB(ipt,rr);
    	
    	swap = SwapFile.getSwapFile(swapPath,swapCapacity);
    	
		tlbLock = new Lock();
		
		pageLock = new Lock();

		pages =  new IPTPage();
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
    
    /*
     * Mem allocate
     */
    public static int getFreePageNum(){
    	int size = -1;
    	
    	pageLock.acquire();
    	
    	size = freePageSet.size();
    	
    	pageLock.release();
    	
    	return size;
    }
    
    
    public static int allocatePage(int pid){
    	int firstFreePage = -1;

    	boolean lockSet = false; 
    	if(!pageLock.isHeldByCurrentThread()){
    		pageLock.acquire();
    		lockSet = true;
    	}
    		
    	// if no more free page, swap! 
    	if (freePageSet.size() == 0) {    		
    		
    		String outKey = pages.evict();
    		String[] parts = outKey.split(":");
    		int outPid = Integer.parseInt(parts[0]);
    		int outVpn = Integer.parseInt(parts[1]);
    		int outPpn = Integer.parseInt(parts[2]);
    		
    		Lib.debug(dbgVM, "outPid " + outPid + " outVpn " + outVpn);
    		
        	// if page to swap out is dirty, write to swap
    		if(getCoreMap().get(outPid).get(outVpn).dirty){
    			swap.writeToSwap(outPid, 
    					outVpn, getCoreMap().get(outPid).get(outVpn).ppn);
    		}
    		
    		// add it back to free page list.
    		freePageSet.add(outPpn);
    		
    		// remove for outPid's page table
    		getCoreMap().get(outPid).remove(outVpn);
    		pages.remove(pid+":"+outVpn);
    	}    	
    	
    	if(freePageSet.size() > 0){
    		firstFreePage = freePageSet.first();
    		if(!freePageSet.remove(firstFreePage))
    			firstFreePage = -1;
    		
Lib.debug(dbgVM, "firstFreePage " + firstFreePage);    		
    	}
    	
    	if(lockSet)
    		pageLock.release();
    	
    	return firstFreePage;
    }
    
    
    public static  boolean freePage(int ppn){
    	pageLock.acquire();
    	
    	if(ppn >= 0 && freePageSet.add((Integer)ppn)) {
    		pageLock.release();
    		return true;
    	}
    	
    	pageLock.release();
    	return false;
    }
    
    public static CoreMap getCoreMap() {
    	if(coreMap == null)
    		coreMap = new CoreMap();
    	
    	return coreMap;
    }
    
   static class CoreMap {
    	TreeMap<Integer, TreeMap> coreMap;
    	
    	CoreMap(){
    		coreMap = new TreeMap<Integer, TreeMap>();
    	}
    	
    	public int insert(int pid, TreeMap<Integer,TranslationEntry> pt) {
    		return (coreMap.put(pid, pt)==null?-1:0);
    	}
    	
    	public int remove(int pid) {
    		return (coreMap.remove(pid)==null?-1:0);
    	}
    	
    	public TreeMap<Integer,TranslationEntry> get(int pid) {
    		return coreMap.get(pid);
    	}
    	
    	public void clear(){
    		coreMap.clear();
    	}
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
	
	public static CoreMap coreMap;
	
	private static String swapPath="swap.bin";
	private static int swapCapacity = 64;
}
