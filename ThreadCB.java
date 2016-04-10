package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
 * @OSPProject Threads
 * @author Dion de Jong
 * @email Ddejong@email.sc.edu
 * @version 1.0
 * @Date 2-23-15
 * @class ThreadCB
 * This class is responsible for actions related to manipulating threads, 
 * including creating, killing, dispatching, resuming, and suspending threads.
 * Specifically, these methods will be used to schedule threads using a 
 * Round Robin implementation of scheduling. 
*/

public class ThreadCB extends IflThreadCB 
{
	//Instance Variables & structures
	//The ReadyQueue is globally manipulated and should not be copied by methods. 
	static GenericList ReadyQueue;
	
    /**
    * @OSPProject Threads
    * @method Constructor
    * The thread constructor. This method will create a ThreadCB object.
    * It must call super(); as its first statement.  
    * 
    * @param N/A 
    * @return N/A
    */
    public ThreadCB()
    {
    	super(); 
    }

    /**
     * @OSPProject Threads
     * @method init
     * This method will be called once at the beginning of the
     * simulation. This is where the instance variables may be
     * initialized. For our purposes, this is where the ReadyQueue 
     * is initialized. 
     * 
     * @param N/A 
     * @return N/A
    */
    public static void init()
    {
    	//initialize the ReadyQueue that will hold the threads that are Ready.
    	ReadyQueue = new GenericList();  
    }

    /** 
      * @OSPProject Threads
      * @method do_create
      * Sets up a new thread and adds it to the given task. 
      * The method must set the ready status and attempt to 
      * add thread to task. If the latter fails because there 
      * are already too many threads in this task, so does this 
      * method, otherwise, the thread is appended to the ready 
      * queue and dispatch() is called. 
      *
      * @param task (to attach the thread to) 
	  * @return thread created or null
    */
    static public ThreadCB do_create(TaskCB task)
    {
    	//null task case, dispatch and return null
    	if (task == null)
    	{
    		dispatch();
    		return null;
    	}
    	
    	//Max number of threads case
    	//If a task already has or exceeds the max number of threads, 
    	//we cannot create a new thread. Dispatch and return null. 
    	if (task.getThreadCount() >= MaxThreadsPerTask)
    	{
    		dispatch(); 
    		return null;
    	}
    	
    	//if there are less than the max number of threads, 
    	//we can create a new thread and will do so. 
    	else
    	{
    		//create the thread
    		ThreadCB CreatedThread = new ThreadCB(); 
    		//set the priority to the priority of the task we are attaching it to.
    		CreatedThread.setPriority(task.getPriority());
    		//set the status
    		CreatedThread.setStatus(ThreadReady);
    		//associate the parent task with the thread
    		CreatedThread.setTask(task);  
    		
    		//associate the thread with the task
    		//test if this fails and dispatch and return null if it does.
    		if (task.addThread(CreatedThread) == FAILURE)
    		{
    			dispatch(); 
    			return null; 
    		}
    		
    		//append to the ready queue and dispatch
    		ReadyQueue.append(CreatedThread); 
    		//if none if the if con ditions were met, this line will always execute
    		//so we know we will dispatch, no matter which path was taken. 
    		dispatch();  
    		//return the thread that was created
    		return CreatedThread; 
    	}
    }

    /** 
	* @OSPProject Threads
	* @method do_kill
	* Kills the specified thread. 
	* The status must be set to ThreadKill, the thread must be
	* removed from the task's list of threads and its pending IORBs
	* must be purged from all device queues.
	* If some thread was on the ready queue, it must removed, if the 
	* thread was running, the processor becomes idle, and dispatch() 
	* must be called to resume a waiting thread.
	* 
	* @param task (to attach the thread to) 
	* @return thread created or null
    */
    public void do_kill()
    {
    	//create a temp thread for the try/catch
    	ThreadCB t = null; 
        
    	//save a variable for the status of the Thread
    	int Stat = this.getStatus(); 
       
    	//If it is ready, remove it from the ReadyQueue
    	if (Stat == ThreadReady)
        {
        	ReadyQueue.remove(this); 
        }
        
    	//Running thread case
        if (Stat == ThreadRunning)
        {
        	//try catch block to avoid null pointer exception
        	try 
            {
        		//when checking if this is the real current thread, it is possible for this to be null
        		t = MMU.getPTBR().getTask().getCurrentThread();
            }
        	catch (NullPointerException e)
        	{
        		//what if we catch?
        	}
        	
        	//check if this is the real thread. 
        	if (this == t)
        	{
        		//if it is then we will remove it by pre-empting it. 
        		MMU.setPTBR(null);
        		t.getTask().setCurrentThread(null);
        	}
        }
      
        //remove the thread from its task
        getTask().removeThread(this);
        //set it's status to kill		
        this.setStatus(ThreadKill);
        
        //scan to make sure that is not associated with any IORBs
        for (int i = 0; i < Device.getTableSize(); ++ i)
        {
        	//cancel the IO if it is associated with the dead thread
        	Device.get(i).cancelPendingIO(this); 
        }
        
        //release any other resources that this thread may be holding. 
        ResourceCB.giveupResources(this);
        dispatch(); 
        		
        //lastly, check if the task has any threads left
        //if it has no more threads, kill the task too. 
        if (getTask().getThreadCount() == 0)
        {
        	getTask().kill();
        }   
    }

    /** 
    * @OSPProject Threads
    * @method do_suspend 
    * Suspends the thread that is currenly on the processor on the 
    * specified event. 
    * Note that the thread being suspended doesn't need to be
    * running. It can also be waiting for completion of a pagefault
    * and be suspended on the IORB that is bringing the page in. 
	* Thread's status must be changed to ThreadWaiting or higher,
    * the processor set to idle, the thread must be in the right
    * waiting queue, and dispatch() must be called to give CPU
    * control to some other thread.
    *
    * @param event - event on which to suspend this thread.
    * @return N/A
    * 
    */
    public void do_suspend(Event event)
    {
    	//create a temp thread for the try/catch
    	ThreadCB t = null; 
    	
    	//save a variable for the status of the Thread
    	int Stat = this.getStatus(); 
    	
    	//if it is running, remove the thread by pre-empting it. 
        if (Stat == ThreadRunning)
        {
        	//try catch block to avoid null pointer exception
        	try 
            {
        		//when checking if this is the real current thread, it is possible for this to be null
        		t = MMU.getPTBR().getTask().getCurrentThread();
            }
        	catch (NullPointerException e)
        	{
        		//what if we catch?
        	}
        	
        	//check if this is the real thread. 
        	if (this == t)
        	{
        		//if it is, we will supsend it by prempting it and changing it to waiting. 
        		MMU.setPTBR(null);	
        		t.getTask().setCurrentThread(null);
        		setStatus(ThreadWaiting);	
        	}
        }
        
        else if (Stat >= ThreadWaiting)
        {
        	//If the thread was already waiting, we will increment it
        	setStatus(Stat + 1);
        } 
          
    	//make sure this isn't the ThreadReady case by checking if the thread
        //is in the ReadyQueue, if it isn't we can add it to the event queue and dispatch
        if (ReadyQueue.contains(this) == false)
     	{
     		event.addThread(this); 
     	}
 		dispatch();
    }

    /**
    * @OSPProject Threads 
    * @method do_resume 
    * Resumes the thread.
	* Only a thread with the status ThreadWaiting or higher
	* can be resumed.  The status must be set to ThreadReady or
	* decremented, respectively.
	* A ready thread should be placed on the ready queue.
	* 
	* This method's code was taken from page 41 of the OSP2 manual. 
	* 
	* @param N/A
	* @return N/A
    */
    
    public void do_resume()
    {
    	//in the event a resume is called on a thread that is not waiting. 
    	if(this.getStatus() < ThreadWaiting)
    	{
    		MyOut.print(this, "Attempt to resume " + this + ", which wasn't waiting");
    		return;
    	}

    	//output 
    	MyOut.print(this,  "Resuming " + this);
   
    	//if the thread was waiting, change it to ready. 
    	if(this.getStatus() == ThreadWaiting)
    	{
    		this.setStatus(ThreadReady);
    	}

    	//if the status has several waits, decrement. 
    	else if(this.getStatus() > ThreadWaiting)
    	{
    		this.setStatus(this.getStatus()-1);
    	}

    	//once the status is ready, we can add it to the ready queue. 
    	if(this.getStatus() == ThreadReady)
    	{
    		ReadyQueue.append(this);
    	}
    	dispatch();
    }

    /** 
    * @OSPProject Threads
    * @method do_dispatch() 
    * Selects a thread from the run queue and dispatches it. 
    * If there is just one theread ready to run, reschedule the thread 
    * currently on the processor.
    * In addition to setting the correct thread status it must
    * update the PTBR.
	* 
	* @param N/A
	* @return SUCCESS or FAILURE
    */
    public static int do_dispatch()
    {
    	//temporary thread for try/catch block.
    	ThreadCB t = null;
    	
    	//temporary thread used for dispatch
    	ThreadCB Dispatcht = null; 
        
    	//try catch block to avoid null pointer exception
    	try 
        {
    		//when checking if this is the real current thread, it is possible for this to be null
    		t = MMU.getPTBR().getTask().getCurrentThread();
        }
    	catch (NullPointerException e)
    	{
    		//what if we catch?
    	}
    	
    	//When dispatching a thread, we must stop the current running running thread
    	//to do this, we will pre-empt it, and change it to ready and add it back to 
    	//the ready queue. 
    	if (t != null)
    	{
    		t.getTask().setCurrentThread(null);
    		MMU.setPTBR(null);
    		t.setStatus(ThreadReady); 
    		ReadyQueue.append(t);
    	}
    	
    	//next we will pull the thread we want to dispatch
    	//if there are no more threads in the Ready Queue, we fail and the program ends. 
    	if (ReadyQueue.isEmpty())
    	{
    		MMU.setPTBR(null);
    		return FAILURE; 
    	}
    	
    	//otherwise, we will take the thread from the head of the ReadyQueue (since
    	//we are using RR scheduling). 
    	else 
    	{
    		//we must convert this object to a ThreadCB object
    		Dispatcht = (ThreadCB) ReadyQueue.removeHead(); 
    		//we will assign it the running status, tell it that it is the current thread,
    		//and update the PTBR. 
    		MMU.setPTBR(Dispatcht.getTask().getPageTable());
    		Dispatcht.getTask().setCurrentThread(Dispatcht);
    		Dispatcht.setStatus(ThreadRunning); 
    		//lastly we set a quantum of 50 and are done!
    		HTimer.set(50);
    		return SUCCESS; 
    	}
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }
}
