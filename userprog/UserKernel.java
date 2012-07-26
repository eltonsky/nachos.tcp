package nachos.userprog;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
    	super();
    	
    	freePageSet = new TreeSet<Integer>();
    	for(int i =0; i<Machine.processor().getNumPhysPages();i++){
    		freePageSet.add(i);
    	}
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
		super.initialize(args);
	
		console = new SynchConsole(Machine.console());
		
		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() { exceptionHandler(); }
		});

    	kPageLock = new Lock();
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
//    	Lib.debug('t', "#### selfTest().");
//    	
//		super.selfTest();
//	
//		System.out.println("Testing the console device. Typed characters");
//		System.out.println("will be echoed until q is typed.");
//	
//		char c;
	
//		do {
//		    c = (char) console.readByte(true);
//		    console.writeByte(c);
//		}
//		while (c != 'q');
	
//		System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);
	
		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
    	Lib.debug('t', "#### UserKernel run().");
    	
		super.run();
	
		UserProcess process = UserProcess.newUserProcess();
		
		String shellProgram = Machine.getShellProgramName();	
		Lib.assertTrue(process.execute(shellProgram, Machine.getUserProgArgs()));
	
		KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
    	Lib.debug('t', "#### terminate().");
    	
    	super.terminate();
    }

    public static int getFreePageNum(){
    	int size = -1;
    	
    	kPageLock.acquire();
    	
    	size = freePageSet.size();
    	
    	kPageLock.release();
    	
    	return size;
    }
    
    public static int allocatePage(){
    	int firstFreePage = -1;
    	
    	kPageLock.acquire();
    	
    	if(freePageSet.size() > 0){
    		firstFreePage = (Integer)freePageSet.first();
    		if(!freePageSet.remove(firstFreePage))
    			firstFreePage = -1;
    	}
    	
    	kPageLock.release();
    	
    	return firstFreePage;
    }
    
    public static  boolean freePage(int ppn){
    	kPageLock.acquire();
    	
    	if(ppn >= 0 && freePageSet.add((Integer)ppn)) {
    		kPageLock.release();
    		return true;
    	}
    	
    	kPageLock.release();
    	return false;
    }
    
    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    
    private static SortedSet<Integer> freePageSet;
    private static Lock kPageLock;
}
