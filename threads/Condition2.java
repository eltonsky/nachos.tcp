package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();
	
	conditionLock.release();
	
	Lib.debug('t', "### Put " + KThread.currentThread().toString() +" to sleep");
	
	waitThreadQueue.waitForAccess(KThread.currentThread());
	KThread.sleep();
	
	conditionLock.acquire();
	
	Machine.interrupt().restore(intStatus);
	
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
	boolean intStatus = Machine.interrupt().disable();

	KThread thread = waitThreadQueue.nextThread();
	
	Lib.debug('t', "thread from nextThread is " + ((thread==null)?"null":"not null"));
	
	if(thread != null) {
		thread.ready();
		Lib.debug('t', "### Condition2.wake(), put " + thread.toString() + " to ready.");
	}
	
	Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
	KThread thread = null;
	
	Lib.debug('t', "waitThreadQueue.isEmpty() " + waitThreadQueue.isEmpty());
	
	while (!waitThreadQueue.isEmpty())
	    wake();
    }
    
    // only works for PQ 
    public void printWaitQueue(){
    	boolean intStatus = Machine.interrupt().disable();
    	
    	((PriorityQueue)waitThreadQueue).print();
    	
    	Machine.interrupt().restore(intStatus);
    }

    private Lock conditionLock;
    private ThreadQueue waitThreadQueue =
    		ThreadedKernel.scheduler.newThreadQueue(false);
}
