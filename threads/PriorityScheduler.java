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
			            return (o1.getEffectivePriority() - o2.getEffectivePriority());
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
		    
		    System.out.print("## PQ INFO ##");
		    
		    System.out.print("transferPriority:"+this.transferPriority);
		    
		    if(this.lockHolderTS != null)
		    	System.out.print("lockHolder Thread:"+this.lockHolderTS.thread.toString() + 
		    			", priority: " + this.lockHolderTS.priority + ", donation: " + 
		    			this.lockHolderTS.donation + ", in queue time: " + this.lockHolderTS.inQueueTime);
		    else
		    	System.out.print("There's no lockHolder.");
		    
		    Iterator<ThreadState> iter = waitingThreadState.iterator();
		    System.out.print("Waiting ThreadState: ");
		    
		    while(iter.hasNext()) {
		    	ThreadState ts = iter.next();
		    	System.out.print("thread :"+ts.thread.toString() + ", priority: "
		    			+ ts.priority + ", donation: " + ts.donation + 
		    			", in queue time: " + ts.inQueueTime);
		    }
		    
		    System.out.print("## END PQ INFO ##");
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
			this.waitInQueue = waitQueue;
			this.inQueueTime = Machine.timer().getTime();
			waitQueue.waitingThreadState.offer(this);
			
			if (waitQueue.transferPriority){
				visitedPQ = new ArrayList<PriorityQueue>();
				this.caculateDonation(waitQueue);
			}
		}
		
		/*
		 * get highest priority from other threads waiting on resources locked by
		 * this thread. Suppose visitedPQ is reset before calling this from 
		 * getEffectivePriority.
		 */
		protected void caculateDonation(PriorityQueue queue) {			
			if (queue.lockHolderTS == null) {
				Lib.debug('t', "NO LOCK on queue");
				return;
			}
			
			if (visitedPQ.contains(queue)) {
				Lib.debug('t', "See the same queue again ! Possible DeadLock !!!");
				return;
			}
			
			if (queue.lockHolderTS.getEffectivePriority() == PriorityScheduler.priorityMaximum) {
				Lib.debug('t', "The lockHolder already has max priority.");
				return;
			}
			
			int currDonation = this.priority;
			int maxQueuePriority = priorityMinimum;
			
			for(PriorityQueue pq : acquiredQueue) {
				maxQueuePriority = (pq.waitingThreadState.peek().getEffectivePriority());
				currDonation = Math.max(maxQueuePriority, currDonation);
			}
			
			queue.lockHolderTS.donation = currDonation;
			
			//re-sort lockHolder
			queue.waitingThreadState.remove(queue.lockHolderTS);
			queue.waitingThreadState.offer(queue.lockHolderTS);
			
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
			if (waitQueue.transferPriority) {
				waitQueue.lockHolderTS = this;

				acquiredQueue.add(waitQueue);
				
				visitedPQ = new ArrayList<PriorityQueue>();
				this.caculateDonation(waitQueue);
				
				// coz this thread already get access, it's no
				// longer waiting for this.
				this.waitInQueue = null;
			}
		}	
	
		/** The thread with which this object is associated. */	   
		protected KThread thread;
		protected long inQueueTime;
		/** The priority of the associated thread. */
		protected int priority;
		protected int donation;
		private boolean recomputePriority;
		
		// the queue this thread is waiting in
		protected PriorityQueue waitInQueue;
		
		// list of PQs for resources this thread is holding
		protected List<PriorityQueue> acquiredQueue;
		
		// list of PQs visited when call donatePriority recursively;
		// this is reseted each time before calling donatePriority.
		// kinda of deadlock detect.
		protected List<PriorityQueue> visitedPQ;
    }
}
