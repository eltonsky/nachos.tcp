package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    public static final int sizeOfQueue = 1000;
    
    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }
    
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
		    this.transferPriority = transferPriority;
		    
		    waitingThreadState = new java.util.PriorityQueue<ThreadState>(sizeOfQueue,
		            new Comparator<ThreadState>() {
			          public int compare(ThreadState o1, ThreadState o2) {
			        	  if (o1.getEffectivePriority() == o2.getEffectivePriority())
			        		  return new Long(o1.inQueueTime).compareTo(new Long (o2.inQueueTime));
			            return (o2.getEffectivePriority() - o1.getEffectivePriority());
			          }
		        });
		}
	
		/*
		 * @see nachos.threads.ThreadQueue#waitForAccess(nachos.threads.KThread)
		 *
		 * if lock.lockHolder exists, put thread to block.
		 */
		public void waitForAccess(KThread thread) {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    getThreadState(thread).waitForAccess(this);
		}
	
		public void acquire(KThread thread) {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    getThreadState(thread).acquire(this);
		}
	
		public KThread nextThread() {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    
		    ThreadState next = pickNextThread();
		    if(next != null){
		    	next.acquire(this);
		    	
		    	waitingThreadState.remove(next);
		    	
		    	return next.thread;
		    }
	
		    return null;
		}
	
		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			return waitingThreadState.peek();
		}
		
		public void print() {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    
		    System.out.println("## PQ INFO ##");
		    
		    System.out.println("transferPriority:"+this.transferPriority);
		    
		    if(this.lockHolderTS != null)
		    	System.out.println("lockHolder Thread:"+this.lockHolderTS.thread.toString() + 
		    			", priority: " + this.lockHolderTS.priority + ", donation: " + 
		    			this.lockHolderTS.donation + ", in queue time: " + this.lockHolderTS.inQueueTime);
		    else
		    	System.out.println("There's no lockHolder.");
		    
		    Iterator<ThreadState> iter = waitingThreadState.iterator();
		    System.out.println("Waiting ThreadState: ");
		    
		    while(iter.hasNext()) {
		    	ThreadState ts = iter.next();
		    	System.out.println("thread :"+ts.thread.toString() + ", priority: "
		    			+ ts.priority + ", donation: " + ts.donation + 
		    			", in queue time: " + ts.inQueueTime);
		    }
		    
		    System.out.println("## END PQ INFO ##");
		}
	
		
		@Override
		public boolean isEmpty() {
			return this.waitingThreadState.isEmpty();
		}
		
		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		public ThreadState lockHolderTS;
		protected java.util.PriorityQueue<ThreadState> waitingThreadState;
	
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
		public ThreadState(KThread thread) {
		    this.thread = thread;
		    
		    setPriority(priorityDefault);
		}
	
		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
		    return priority;
		}
	
		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
		    return (donation > priority)?donation:priority;
		}
	
		
		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
		    if (this.priority == priority)
			return;
		    
		    this.priority = priority;
		    
		    if(this.waitInQueue != null && this.waitInQueue.transferPriority){
		    	caculateDonation(waitInQueue);
		    }
		}
	
		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			Lib.debug('t', this.thread.toString() + " Enter waitForAccess");
			
			this.waitInQueue = waitQueue;
			this.inQueueTime = Machine.timer().getTime();
			waitQueue.waitingThreadState.offer(this);
			
			if (waitQueue.transferPriority){
				visitedPQ = new ArrayList<PriorityQueue>();
				this.caculateDonation(waitQueue);
			}
		}
		
		public boolean equals(ThreadState ts){
			return (ts.thread.toString() == this.thread.toString());
		}
		
		/*
		 * get highest priority from other threads waiting on resources locked by
		 * this thread. Suppose visitedPQ is reset before calling this from 
		 * getEffectivePriority.
		 */
		protected void caculateDonation(PriorityQueue queue) {
			Lib.debug('t', "caculateDonation..");
			
			if (!queue.transferPriority){
				Lib.debug('t', "Not allow transfer priority.");
				return;
			}
			
			if (queue.lockHolderTS == null || queue.lockHolderTS.waitInQueue == null) {
				Lib.debug('t', "NO LOCK on queue OR lockHolder is not wait in any queue.");
				return;
			}
			
			if (visitedPQ.contains(queue)) {
				Lib.debug('t', "See the same queue again ! Possible DeadLock!!! Unless this is called from acquire().");
				return;
			}
			
			if (queue.lockHolderTS.getEffectivePriority() == PriorityScheduler.priorityMaximum) {
				Lib.debug('t', "The lockHolder already has max priority.");
				return;
			}
			
			int currDonation = this.priority;
			int maxQueuePriority = priorityMinimum;
			
			for(PriorityQueue pq : acquiredQueue) {
				ThreadState top = pq.waitingThreadState.peek();
				
				if(top != null) {
					maxQueuePriority = top.getEffectivePriority();
					currDonation = Math.max(maxQueuePriority, currDonation);
				}
			}
			
			queue.lockHolderTS.donation = currDonation;
			Lib.debug('t', "## Donate " + currDonation+" to " + queue.lockHolderTS.thread.toString()
					+", Effective priority:" + queue.lockHolderTS.getEffectivePriority());
			
			//re-sort lockHolder
			queue.lockHolderTS.waitInQueue.print();
			
			boolean removed = queue.lockHolderTS.waitInQueue.waitingThreadState.remove(queue.lockHolderTS);
			if(removed)
				queue.lockHolderTS.waitInQueue.waitingThreadState.offer(queue.lockHolderTS);
			
			Lib.debug('t', "If removed " + removed);
			
			queue.lockHolderTS.waitInQueue.print();
			
			visitedPQ.add(queue);
			
			if (queue.lockHolderTS.waitInQueue != null){
				caculateDonation(queue.lockHolderTS.waitInQueue);
			}
			
			return;
		}
	
		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			Lib.debug('t', "Acquiring PQ ..., waitQueue.transferPriority is "
					+ waitQueue.transferPriority);
			
			waitQueue.lockHolderTS = this;
			waitQueue.lockHolderTS.waitInQueue = null;
			
			if (waitQueue.transferPriority) {
				acquiredQueue.add(waitQueue);
				
				visitedPQ = new ArrayList<PriorityQueue>();
				this.caculateDonation(waitQueue);
				
				// coz this thread already get access, it's no
				// longer waiting for this.
				this.waitInQueue = null;
				waitQueue.waitingThreadState.remove(this);
			}
		}	
	
		/** The thread with which this object is associated. */	   
		protected KThread thread;
		protected long inQueueTime;
		/** The priority of the associated thread. */
		protected int priority;
		protected int donation;
		//private boolean recomputePriority;
		
		// the queue this thread is waiting in
		protected PriorityQueue waitInQueue;
		
		// list of PQs for resources this thread is holding
		protected List<PriorityQueue> acquiredQueue = new ArrayList<PriorityQueue>();
		
		// list of PQs visited when call donatePriority recursively;
		// this is reseted each time before calling donatePriority.
		// kinda of deadlock detect.
		protected List<PriorityQueue> visitedPQ;
    }
    
    
    private static void DonationTest1(){
    	
    	boolean oldP;
    	final Lock lock1 = new Lock();
    	final Lock lock2 = new Lock();
    	
    	// low priority thread
    	KThread lowKt1 = new KThread(new Runnable() {
    		public void run() {
    			lock1.acquire();
    			
    			System.out.println("Low thread 1 acquired lock1");
    			
    			for(int i=1; i <=3; i++) {
    				System.out.println("Low thread 1 running "+i+" times ...");
    				KThread.yield();
    			}
    			
    			System.out.println("Low thread 1 releasing lock1 ...");
    			
    			lock1.release();
    		}
    	}).setName("Low Thread 1");
    	
    	oldP = Machine.interrupt().disable();
    	ThreadedKernel.scheduler.setPriority(lowKt1, 1);
    	Machine.interrupt().restore(oldP);
    	
    	// low priority thread
    	KThread lowKt2 = new KThread(new Runnable() {
    		public void run() {
    			lock2.acquire();
    			
    			System.out.println("Low thread 2 acquired lock2");
    			
    			for(int i=1; i <=3; i++) {
    				System.out.println("Low thread 2 running "+i+" times ...");
    				KThread.yield();
    			}
    			
    			System.out.println("Low thread 2 releasing lock2 ...");
    			
    			lock2.release();
    		}
    	}).setName("Low Thread 2");
    	
    	oldP = Machine.interrupt().disable();
    	ThreadedKernel.scheduler.setPriority(lowKt2, 1);
    	Machine.interrupt().restore(oldP);
    	
    	// high priority thread
    	KThread highKt = new KThread(new Runnable() {
    		public void run() {
    			lock1.acquire();
    			
    			System.out.println("High thread acquired lock1");
    			
    			lock1.release();
    			
    			System.out.println("High thread released lock1");
    			
    			lock2.acquire();
    			
    			System.out.println("High thread acquired lock2");
    			
    			lock2.release();
    			
    			System.out.println("High thread released lock2");
    		}
    	}).setName("High Thread");
    	
    	oldP = Machine.interrupt().disable();
    	ThreadedKernel.scheduler.setPriority(highKt, 6);
    	Machine.interrupt().restore(oldP);
    	
    	// middle priority thread
    	KThread middleKt = new KThread(new Runnable() {
    		public void run() {    			
    			for(int i=1;i<=3;i++) {
	    			System.out.println("Middle thread running "+i+" times ...");
	    			
	    			KThread.yield();
    			}
    		}
    	}).setName("Middle Thread");
    	
        oldP = Machine.interrupt().disable();
    	ThreadedKernel.scheduler.setPriority(middleKt, 4);
    	Machine.interrupt().restore(oldP);
    	
    	lowKt1.fork();
    	lowKt2.fork();
    	
    	//start low thread, let it acquire lock1
    	KThread.yield();
    	
    	middleKt.fork();
    	highKt.fork();
    	
    	KThread.yield();
    	
    	highKt.join();    	
    	middleKt.join();
    	lowKt1.join();
    	lowKt2.join();
    }
    
    private static class PQTest implements Runnable {
		PQTest(int priority, String name) {
		    this.priority = priority;
		    this.name  = name;
		}
		
		public void run() {
			
		    for (int i=0; i<5; i++) {
				Lib.debug('t',"## " + name + "with priority "+priority+" has run "+i+" times");
				KThread.yield();
		    }
		}
	
		private int priority;
		private String name;
    }
    
    
    private static void basicPQTest(){
    	List<KThread> threadList = new ArrayList<KThread>();
		for(int i=0; i<5; i++) {
			KThread kt = new KThread(new PQTest((100+i)%PriorityScheduler.priorityMaximum, "PQTest"+i)).setName("PQTest"+i);
			
			System.out.println("PQTest"+i+" priority " + (100+i)%PriorityScheduler.priorityMaximum);
			
			boolean oldP = Machine.interrupt().disable();
			
			ThreadedKernel.scheduler.setPriority(kt, (100+i)%PriorityScheduler.priorityMaximum);
			
			Machine.interrupt().restore(oldP);
			
			threadList.add(kt);
			
			kt.fork();
		}
		
		System.out.println(" main priority " + ThreadedKernel.scheduler.getEffectivePriority(KThread.currentThread()));
		
		KThread.yield();
		
		for(KThread kt : threadList){
			kt.join();
		}	
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
    	//basicPQTest();
		DonationTest1();
    	
    }
}





