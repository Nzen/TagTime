/*
 * Copyright 2011 Joseph Cloutier, Daniel Reeves, Bethany Soule
 * 
 * This file is part of TagTime.
 * 
 * TagTime is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * TagTime is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with TagTime. If not, see <http://www.gnu.org/licenses/>.
 */

package tagtime.ping;

import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.TreeSet;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import tagtime.Main;
import tagtime.settings.SettingType;
import tagtime.util.HMSTimeFormatter;
import tagtime.util.TagCount;

/**
 * A job that gets executed by the Quartz scheduler when it is time to
 * ping the user. This does little more than create and display a
 * PingWindow.
 */
public class PingJob implements Job {
	protected final Window window;
	
	private long scheduledTime;
	private boolean dataLogged = false;
	
	@SuppressWarnings("unchecked")
	public PingJob() throws ClassCastException {
		Object cachedTags = Main.getSettings().getValue(SettingType.CACHED_TAGS);
		window = new PingWindow(this, ((TreeSet<TagCount>) cachedTags).toArray());
	}
	
	@Override
	public void execute(JobExecutionContext context) {
		scheduledTime = context.getScheduledFireTime().getTime();
		
		if(context.getPreviousFireTime() == null) {
			//the first job is run immediately at the start of the session
			//but this doesn't match the actual time it should have been
			//run, so skip it
			dataLogged = true;
			window.dispose();
			return;
		}
		
		//if this job was executed too long after it was scheduled,
		//log it as "afk off RETRO"
		if(scheduledTime - System.currentTimeMillis() > (Integer) Main.getSettings()
						.getValue(SettingType.WINDOW_TIMEOUT) * 1000) {
			Main.getLog().logRetro(scheduledTime, "afk off");
		}
		
		//this is probably the time since the previous ping was scheduled
		//to run
		long timeSincePreviousPing = scheduledTime - context.getPreviousFireTime().getTime();
		System.out.println("Dispatching ping after a wait of "
						+ HMSTimeFormatter.format(timeSincePreviousPing / 1000)
						+ ".");
		
		window.setVisible(true);
	}
	
	public void cancel() {
		window.dispose();
	}
	
	/**
	 * Submits the given text as the tag(s) entered.
	 */
	public void submit(String tags) {
		if(!dataLogged) {
			dataLogged = true;
			
			Main.getLog().log(scheduledTime, tags);
			Main.getSettings().incrementCounts(SettingType.CACHED_TAGS,
						new LinkedList<String>(Arrays.asList(tags.split(" "))));
		}
	}
	
	/**
	 * Submits the ping canceled message, unless data has already been
	 * submitted.
	 */
	public void submitCanceled() {
		if(!dataLogged) {
			dataLogged = true;
			
			if(System.currentTimeMillis() - scheduledTime > (Integer) Main.getSettings()
						.getValue(SettingType.WINDOW_TIMEOUT) * 1000) {
				//use an underscore because timed_out should be treated
				//as a single tag
				Main.getLog().logRetro(scheduledTime, "timed_out");
			} else {
				Main.getLog().logRetro(scheduledTime, "canceled");
			}
		}
	}
}
