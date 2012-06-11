package nachos.threads;

import java.util.ArrayList;
import java.util.Iterator;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	Lib.debug('t', "### TimerInterrupt at " + Machine.timer().getTime() + ", thread: " + KThread.currentThread().toString());
    	
    	boolean intStatus = Machine.interrupt().disable();
    
    	Iterator<ThreadTime> iter = waitAlarmThread.iterator();
    	while(iter.hasNext()){
    		ThreadTime tt = (ThreadTime)iter.next();
    		
    		if(Machine.timer().getTime() > tt.getWakeTime()) {
    			
    			Lib.debug('t', "### Put " + tt.getThread().toString() + " to ready queue.");
    			
    			tt.getThread().ready();
    			iter.remove();
    		}
    	}
    	
    	Machine.interrupt().restore(intStatus);
    	KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	
	long wakeTime = Machine.timer().getTime() + x;
	
	boolean intStatus = Machine.interrupt().disable();

	Lib.debug('t',"### Add " + KThread.currentThread().toString() + " to Alarm queue; wakeTime " + wakeTime);
	waitAlarmThread.add(new ThreadTime(wakeTime,KThread.currentThread()));
	KThread.sleep();
	
	Machine.interrupt().restore(intStatus);
    }
    
    public static void selfTest() {
    	Lib.debug('t', "Enter Alarm.selfTest");
    	
    	Alarm a1 = new Alarm();
    	a1.waitUntil(1000);
    }
    
    private ArrayList<ThreadTime> waitAlarmThread = new ArrayList<ThreadTime>();
  
    class ThreadTime {
    	long ticks;
    	KThread thread;
    	
    	ThreadTime(long ticks, KThread thread) {
    		this.ticks = ticks;
    		this.thread = thread;
    	}
    	
    	public long getWakeTime() {
    		return ticks;
    	}
    	
    	public KThread getThread() {
    		return thread;
    	}
    }
}
