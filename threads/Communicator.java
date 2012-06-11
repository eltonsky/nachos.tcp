package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	condLock  = new Lock();
    	speakCond = new Condition2(condLock);
    	listenCond = new Condition2(condLock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	condLock.acquire();
    	
    	speakCond.sleep();
    	
    	theWord = word;
    	
    	listenCond.wake();
    	
    	condLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	condLock.acquire();
    	
    	speakCond.wake();
    	
    	listenCond.sleep();
    	
    	condLock.release();
    	
    	return theWord;
    }
    
    /**
     */
    private static Communicator comm;
    
    private static class TestSpeaker implements Runnable {
    	TestSpeaker(int word) {
		    this.word = word;
		}
		
		public void run() {
			Lib.debug('t', "### Speak " + word + " from " + KThread.currentThread().toString());
			
			comm.speak(word);
		}
	
		private int word;
    }
    
    private static class TestListener implements Runnable {
    	TestListener() {
		}
		
		public void run() {
			Lib.debug('t', "### Try to listen from " + KThread.currentThread().toString());
			
		    int res = comm.listen();
		    
		    Lib.debug('t', "### listened " + res + " from " + KThread.currentThread().toString());
		}
    }
    
    public static void selfTest() {
    	comm = new Communicator();
    	
    	KThread t2 = new KThread(new TestListener()).setName("Comm thread 2 - listener");
    	t2.fork();
    	
    	KThread t1 = new KThread(new TestSpeaker(123)).setName("Comm thread 1 - speaker");
    	t1.fork();
    	
    	t1.join();
        t2.join();
    	
    	KThread.yield();
    }
    
    private int theWord;
    private Condition2 speakCond;
    private Condition2 listenCond;
    private Lock condLock;
}
