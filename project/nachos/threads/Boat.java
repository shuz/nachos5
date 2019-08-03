package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat
{
    static BoatGrader bg;

    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 2, b);

  	System.out.println("\n ***Testing Boats with 2 children, 3 adults***");
  	begin(3, 2, b);

  	System.out.println("\n ***Testing Boats with 4 children, 4 adults***");
  	begin(4, 4, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

	// Instantiate global variables here

	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.

	Boat boat = new Boat();
	boat.lock.acquire();
	for (int i = 0; i < adults; ++i) {
	    new KThread(boat.new Adult()).setName("Adult " + i).fork();
	}
	for (int i = 0; i < children; ++i) {
	    new KThread(boat.new Child()).setName("Child " + i).fork();
	}
	
	boat.sleep();
	boat.lock.release();
	
	System.out.println("*** transfer finished");
	
	Lib.assert(boat.oahuAdultNum == 0 && boat.oahuChildNum == 0);
    }
    
    void sleep() {
	cond.sleep();
    }
    
    void wake() {
	cond.wake();
    }

    static final int OAHU = 1;
    static final int MOLOKAI = 2;
    
    int oahuAdultNum;
    int oahuChildNum;
    int molokaiAdultNum;
    int molokaiChildNum;
    
    boolean molokaiHasMoreChildrenBroughtByChild = false;
    boolean oahuHasNoMorePersonBroughtByChild = false;
    
    Lock lock = new Lock();
    MyBoat boat = new MyBoat();
    Condition2 cond = new Condition2(lock);
    
    class MyBoat {
	Condition2 oahuCond = new Condition2(lock);
	Condition2 molokaiCond = new Condition2(lock);
	
	int place = OAHU;
	
	Child child1, child2;
	Adult adult;
	
	void addChild(Child child) {
	    Lib.assert(adult == null);
	    if (child1 == null) {
		child1 = child;
	    } else
	    if (child2 == null) {
		child2 = child;
	    } else {
		Lib.assert(false);
	    }
	}
	
	void addAdult(Adult adult) {
	    Lib.assert(child1 == null && child2 == null);
	    Lib.assert(this.adult == null);
	    
	    this.adult = adult;
	}
	
	void go(int place) {
	    Lib.assert(place != this.place);
	    Lib.assert(child1 != null || adult != null);
	    if (child1 != null) {
		switch (place) {
		case OAHU:
		    bg.ChildRowToOahu();
		    ++oahuChildNum;
		    --molokaiChildNum;
		    break;
		case MOLOKAI:
		    bg.ChildRowToMolokai();
		    ++molokaiChildNum;
		    --oahuChildNum;
		    break;
		}
		child1.place = place;
		
		if (child2 != null) {
                    switch (place) {
                    case OAHU:
                        bg.ChildRideToOahu();
                        ++oahuChildNum;
                        --molokaiChildNum;
                        break;
                    case MOLOKAI:
                        bg.ChildRideToMolokai();
                        ++molokaiChildNum;
                        --oahuChildNum;
                        break;
                    }
                    child2.place = place;
                }	    
	    } else
	    if (adult != null) {
		switch (place) {
		case OAHU:
		    bg.AdultRowToOahu();
		    ++oahuAdultNum;
		    --molokaiAdultNum;
		    break;
		case MOLOKAI:
		    bg.AdultRowToMolokai();
		    ++molokaiAdultNum;
		    --oahuAdultNum;
		    break;
		}
		adult.place = place;
	    }
	    this.place = place;
	}
	
	void sleep(int place) {
	    switch (place) {
	    case OAHU:
		oahuCond.sleep();
		break;
	    case MOLOKAI:
		molokaiCond.sleep();
		break;
	    }
	}
	
	void wake() {
	    switch (place) {
	    case OAHU:
		oahuCond.wakeAll();
		break;
	    case MOLOKAI:
		molokaiCond.wakeAll();
		break;
	    }
	}
    }
    
    
    /**
     * adult's strategy:
     * if I am in Oahu and boat is present,
     *   if the returned child say there are more children in Molokai,
     *     try to get on the boat.
     *     row to Molokai.
     *   
     * @author mickycat
     *
     */
    class Adult implements Runnable {
	Adult() {
	    ++oahuAdultNum;
	    place = OAHU;
	}
	
	public void run() {
	    lock.acquire();
	    while (true) {
                if (place == OAHU && boat.place == OAHU) {
                    if (molokaiHasMoreChildrenBroughtByChild) {
                        if (boat.adult == null) {
                            boat.addAdult(this);
                            boat.go(MOLOKAI);
                            boat.adult = null;
                            boat.wake();
                        }
                    }
                } else 
                if (place == MOLOKAI) {
                    break;
                }
                boat.sleep(place);
	    }
	    lock.release();
	}
	
	private int place;
    }
    
    /**
     * child's strategy:
     * if I am in Oahu and boat is present,
     *   if there are no children in Molokai or there is no adult in Oahu
     *   try to get on
     *   if I'm the first child on boat
     *     wait for another child if there is
     *     row to Molokai.
     *     if we are the last persons in Oahu
     *       signal the finish of transfering
     *   else (I'm the second child on boat)
     *     wake the first child
     *     sleep until Molokai :)
     * if I am in Molokai and boat is present,
     *   try to get on
     *   check is there any more children in Molokai
     *   row back to Oahu.
     *   tell others the information
     * 
     * @author mickycat
     *
     */
    class Child implements Runnable {
	private Condition2 condition = new Condition2(lock);

	Child() {
	    ++oahuChildNum;
	    place = OAHU;
	}

	public void sleep() {
	    condition.sleep();
	}
	
	public void wake() {
	    condition.wake();
	}
	
	public void run() {
	    lock.acquire();
	    while (true) {
                if (place == OAHU && boat.place == OAHU) {
                    if (!molokaiHasMoreChildrenBroughtByChild || oahuAdultNum == 0) {
                        if (boat.child1 == null) {
                            boat.addChild(this);
                            if (oahuChildNum >= 2) {
                        	boat.wake();
                        	sleep();
                        	boat.go(MOLOKAI);
                        	boat.child1 = null;
                        	boat.child2.wake();
                        	sleep();
                            } else {
                        	boolean lastPerson = oahuAdultNum == 0;
                        	boat.go(MOLOKAI);
                        	boat.child1 = null;
                        	if (lastPerson) {
                        	    Boat.this.wake();
                        	    oahuHasNoMorePersonBroughtByChild = true;
                        	}
                            }
                            boat.wake();
                        } else
                        if (boat.child2 == null) {
                            boolean lastPerson = oahuChildNum == 2 && oahuAdultNum == 0;
                            boat.addChild(this);
                            Child partner = boat.child1;
                            partner.wake();
                            sleep();
                            boat.child2 = null;
                            if (lastPerson) {
                        	Boat.this.wake();
                        	oahuHasNoMorePersonBroughtByChild = true;
                            }
                            partner.wake();
                        }
                    }
                } else
                if (place == MOLOKAI && boat.place == MOLOKAI) {
                    if (oahuHasNoMorePersonBroughtByChild) {
                	break;
                    } else
                    if (boat.child1 == null) {
                	// collect information in Molokai
                	boolean hasMoreChildren = molokaiChildNum > 1;
                	
                	boat.addChild(this);
                	boat.go(OAHU);
                	boat.child1 = null;
                	
                	// broadcast information in Oahu
                	molokaiHasMoreChildrenBroughtByChild = hasMoreChildren;
                	boat.wake();
                    }
                }
		boat.sleep(place);
	    }
	    lock.release();
	}
	
	private int place;
    }
}
