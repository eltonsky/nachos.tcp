package nachos.threads;
import java.util.ArrayList;
import java.util.List;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;
import nachos.machine.Machine;

public class Boat
{
    static BoatGrader bg;

    static Lock lock = new Lock();
     
    static enum LocateAt {Oahu, Molokai}; 
    
    static LocateAt boatAt;
    
    static Integer childrenOnSrc = 0;
    static Integer childrenOnDest= 0;
    static Integer adultOnSrc = 0;
    
    static Condition2 boatCond = new Condition2(lock);
    static Condition2 childOnSrcCond = new Condition2(lock);
    static Condition2 childOnDestCond = new Condition2(lock);
    static Condition2 adultOnSrcCond = new Condition2(lock);
    
    static int boatChildCapacity = 2;
    static int boatAdultCapacity = 1;
    
    public static void selfTest()
    {
		BoatGrader b = new BoatGrader();
		
		//System.out.println("\n ***Testing Boats with only 2 children***");
		//begin(0, 2, b);
	
	//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
	//  	begin(1, 2, b);
	
	  	System.out.println("\n ***Testing Boats with 4 children, 3 adults***");
	  	begin(13, 14, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;
		//init 
		List<KThread> childThreads = new ArrayList<KThread>();
		List<KThread> adultThreads = new ArrayList<KThread>();
		childrenOnSrc = children;
		adultOnSrc = adults;
		boatAt = LocateAt.Oahu;
		
		// Instantiate global variables here
		
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
	

	    Runnable Child = new Runnable() {
		    public void run() {
		    		LocateAt childAt = LocateAt.Oahu;
		    		ChildItinerary(childAt);
	            }
	        };
	    
        for(int i=0;i<children;i++){
		    KThread t = new KThread(Child);
		    t.setName("Child " + i);
		    childThreads.add(t);
		    t.fork();
	    }
		
		Runnable Adult = new Runnable() {
		    public void run() {
	                AdultItinerary();
	            }
	        };
	           
	    for(int i=0;i<adults;i++){
		    KThread t = new KThread(Adult);
		    t.setName("Adult " + i);
		    adultThreads.add(t);
		    t.fork();
	    }
        
	    // join
	    // only joint children coz children thread must
	    // finish after adults cross.
	    for(KThread kt : childThreads){
	    	kt.join();
	    }
    }

    static void AdultItinerary()
    {
		lock.acquire();
		
		while(!canTakeAdult()){
			printStat();
			Boat.adultOnSrcCond.sleep();
		} 
		
		Lib.debug('t', "Adult Proceeding");
			
		Boat.boatAdultCapacity--;
		// no turning back for adult.
		Boat.adultOnSrc--;
		
		bg.AdultRowToMolokai();

		Boat.boatAdultCapacity++;
		
		boatAt = LocateAt.Molokai;
		
		Lib.debug('t', "@@ Before Adult childOnDestCond.wakeAll()");
		Boat.childOnDestCond.printWaitQueue();
		Boat.childOnDestCond.wakeAll();
		Lib.debug('t', "@@ After Adult childOnDestCond.wakeAll()");
		
		lock.release();
    }

    
    static void ChildItinerary(LocateAt childAt)
    {
    	//Lib.debug('t', KThread.currentThread().toString() + ", Try to acquire lock.");
    	
    	lock.acquire();
    	
    	//Lib.debug('t', KThread.currentThread().toString() + ", Acquired lock.");
    	
    	//Lib.debug('t', KThread.currentThread().toString() + ", childAt is " + childAt + ", isBoatEmpty() " + isBoatEmpty());
    	
    	boolean intStatus;
    	
    	while(true){    		
    		printStat();
    		
    		//intStatus = Machine.interrupt().disable();
    		
    		if(childAt == LocateAt.Oahu && boatAt == LocateAt.Oahu) {
    			if(isBoatEmpty()){
    				
    				Lib.debug('t', KThread.currentThread().toString() + ", Boat is Empty.");
    				
	    			// if can wait for the other child to come along
	    			if(childrenOnSrc > 1) {
	    				Boat.boatChildCapacity--;
	    				
			    		while(Boat.boatChildCapacity != 0){
			    			Lib.debug('t', "** is empty, sleep on childOnSrcCond.");
			    			Boat.childOnSrcCond.wakeAll();
			        		Boat.childOnSrcCond.sleep();
			    		}
	    			} else {
	    				Boat.boatChildCapacity--;
	    			}

		    		bg.ChildRowToMolokai();
		    		
		    		Boat.childrenOnDest++;
		    		Boat.childrenOnSrc--;
		    		
		    		childAt = LocateAt.Molokai;
		    		boatAt = LocateAt.Molokai;
		    		
		    		Boat.boatChildCapacity++;
		    		
		    		Boat.childOnDestCond.wakeAll();
		    		// NOTE: this simulation can only finish with 2 children
		    		// as last; assume there are at least 2 children.
		    		
    	    	}  else if (Boat.boatChildCapacity == 1) {
    	    		
    	    		Lib.debug('t', KThread.currentThread().toString() + ", Boat is not Empty.");
    	    		
		    		//Boat.boatCond.sleep();
		    		
    	    		Boat.boatChildCapacity--;
		    		Boat.childOnSrcCond.wakeAll();
		    		
		    		Lib.debug('t', KThread.currentThread().toString() + ", Child going to sleep.");
		    		
		    		Lib.debug('t', "**is not empty, sleep on childOnDestCond.");
		    		Boat.childOnDestCond.sleep();
		    		
		    		Lib.debug('t', KThread.currentThread().toString() + ", Child is waken up.");
		    		
		    		Boat.childrenOnDest++;
		    		Boat.childrenOnSrc--;

    	    		bg.ChildRideToMolokai();
		    				    		
		    		childAt = LocateAt.Molokai;
		    		
		    		Boat.boatChildCapacity++;
		    		
		    		if(isFinish()) {
		    			Lib.debug('t', KThread.currentThread().toString() + " Dectected finishing...");
		    			childOnDestCond.wakeAll();
		    			break;
		    		}
		    		
		    		Boat.childOnDestCond.wakeAll();
		    		
    	    	} else {
    	    		Lib.debug('t', "**is full, sleep on childOnSrcCond.");
    	    		Boat.childOnSrcCond.sleep();
    	    	}
    			
    		} else if (childAt == LocateAt.Molokai && boatAt == LocateAt.Molokai) {
    			
    			if(Boat.boatChildCapacity ==2 && (adultOnSrc > 0 || childrenOnSrc > 0)){    			
    				
    				Boat.boatChildCapacity--;
    				
	    			bg.ChildRowToOahu();
	    			
	    			Boat.childrenOnDest--;
		    		Boat.childrenOnSrc++;
	    			
	    			Boat.boatChildCapacity++;
	    			
	    			childAt = LocateAt.Oahu;
	    			boatAt = LocateAt.Oahu;
	    			
	    			Boat.adultOnSrcCond.wakeAll();
	    			Boat.childOnSrcCond.wakeAll();
	    			
	    			if(adultOnSrc > 0){
	    				Lib.debug('t', "**is row back to src, sleep on childOnSrcCond.");
	    				Boat.childOnSrcCond.sleep();
	    			}
    			} else {
    				Lib.debug('t', "**is on dest, but not ready to go back, sleep on childOnDestCond.");
    				Boat.childOnDestCond.sleep();
    			}
    		}
    		// child waits for boat when it's on source but boat is on dest
    		else if (childAt == LocateAt.Oahu) {
    			Lib.debug('t', "**is on Src but boat on dest, sleep on childOnSrcCond.");
    			Boat.childOnSrcCond.sleep();
    		}
    		// child waits for boat when it's on dest but boat is on source
    		else if (childAt == LocateAt.Molokai){
    			Lib.debug('t', "**is on Dest but boat on src, sleep on childOnDestCond.");
    			Boat.childOnDestCond.sleep();
    		}
    			
    		//Machine.interrupt().restore(intStatus);
    		
    		if(isFinish()) {
    			break;
    		}
    	}
    	
    	lock.release();
    }


    public static boolean isFinish() {
    	return (childrenOnSrc == 0 && adultOnSrc == 0);
    }
    
    public static boolean isBoatEmpty() {
    	return (Boat.boatAdultCapacity == 1 && Boat.boatChildCapacity == 2);
    }
    
    public static boolean canTakeAdult(){
    	return (isBoatEmpty() && (childrenOnDest > 0 || childrenOnSrc == 1) && boatAt == LocateAt.Oahu);
    }
    
    public static void printStat(){
    	System.out.println("boatAdultCapacity is " + boatAdultCapacity + ", boatChildCapacity is " + boatChildCapacity
    			+", childrenOnDest is " + childrenOnDest+", childrenOnSrc is "+ childrenOnSrc+", boatAt " + boatAt);
    }
    
}
