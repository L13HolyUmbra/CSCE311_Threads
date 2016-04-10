package osp.Threads;

import osp.IFLModules.*;
import osp.Utilities.*;
import osp.Hardware.*;

/**  
* @OSPProject Threads
* @author Dion de Jong
* @email Ddejong@email.sc.edu
* @version 1.0
* @Date 2-23-15
* @class TimerInterruptHandler  
* The timer interrupt handler. This class is called upon to
* handle timer interrupts.
*
* @OSPProject Threads
*/
public class TimerInterruptHandler extends IflTimerInterruptHandler
{
    /**
    * @OSPProject Threads
    * @method do_handleInterrupt()
    * This basically only needs to reset the times and dispatch
    * another process.
    * 
    * @param N/A
    * @return N/A
    */
    public void do_handleInterrupt()
    {
    	//dispatch another thread
    	ThreadCB.dispatch(); 
    }
}

