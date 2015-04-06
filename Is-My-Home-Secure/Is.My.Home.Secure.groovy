/**
 *  Is my home secure?
 *  
 *  Version 1.1
 * 
 *  Copyright 2014 Tim Slagle
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Is my home secure?",
    namespace: "tslagle13",
    author: "Tim Slagle",
    description: "Check a set of doors, windows, and locks to see if your home is secure at a certain time or when mode changes.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)


preferences {
	section("Which mode changes trigger the check?") {
		input "newMode", "mode", title: "Which?", multiple: true, required: false
	}
    section("Which doors, windows, and locks should I check?"){
		input "contacts", "capability.contactSensor", title: "Which door(s)?", multiple: true, required: true
        input "locks", "capability.lock", title: "Which lock?", multiple: true, required: false
    }
    section("When should I check? (once per day)") {
    	input "timeToCheck", "time", title: "When?(Optional)", required: false
    }
    section("Vacation mode: Check every X hours. (minimum every 5 hours)") {
    	input "timeToCheckVacation", "number", title: "How often? (Optional)", required: false
    }   
    section("Notification delay... (defaults to 2 min)") {
    	input "falseAlarmThreshold", "decimal", title: "Number of minutes", required: false
  	}
    section("Add SMS alerts?"){
    input "phone", "phone", title: "Phone number (For SMS - Optional)", required: false
		input "pushAndPhone", "enum", title: "Send push message too?", required: false, options: ["Yes","No"]
        
	}
    section("Settings"){
		input "sendPushUnsecure", "enum", title: "Send a SMS/push notification when home is unsecure?", metadata:[values:["Yes","No"]], required:true
        input "sendPushSecure", "enum", title: "Send a SMS/push notification when house is secure?", metadata:[values:["Yes","No"]], required:true
        input "lockAuto", "enum", title: "Lock door(s) automatically if found unsecure?", metadata:[values:["Yes","No"]], required:false
    }
    section(title: "More options", hidden: hideOptionsSection()) {
			input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
            
		}
}



def installed() {
	log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
    unschedule()
    initialize()
}

def initialize(){

	if (newMode != null) {
		subscribe(location, modeChangeHandler)
    }
    if (timeToCheck != null) {
    	schedule(timeToCheck, checkDoor)
    }
initializeVacation()    
}

//set vacation mode if vacation mode is set
def initializeVacation() {
if(allOk){
if (timeToCheckVacation){
    if (timeToCheckVacation > 4)
        // Schedule polling daemon to run every N minutes
        schedule("0 0/${timeToCheckVacation} * * * ?", checkDoor)
}    
}
}

//mode change set check door wait
def modeChangeHandler(evt) {
	log.debug "Mode change to: ${evt.value}"
    // Have to handle when they select one mode or multiple
    if (newMode.any{ it == evt.value } || newMode == evt.value) {
	def delay = (falseAlarmThreshold != null && falseAlarmThreshold != "") ? falseAlarmThreshold * 60 : 2 * 60 
    runIn(delay, "checkDoor")
    }
}

//check doors after delay
def checkDoor(evt) {
if(allOk){
log.debug("checkDoor")
    def openContacts = contacts.findAll { it?.latestValue("contact") == 'open' }
    def openLocks = locks.findAll { it?.latestValue("lock") == 'unlocked' }

   	if (openContacts || openLocks){
    	if (openContacts && openLocks){
    		def message = "ALERT: ${openContacts.join(', ')} and ${openLocks.join(', ')} are unsecure!"
            sendUnsecure(message)

            if (lockAuto != "No"){
            	lockDoors()
            }
        }
        else {
    		def message = "ALERT: ${openContacts.join(', ')} ${openLocks.join(', ')} unsecure!"
            sendUnsecure(message)

            if (lockAuto != "No"){
            	lockDoors()
            }    

        }
    }

    else if (!openContacts && !openLocks){
    	def message = "All doors, windows, and locks are secure"
        sendSecure(message)
   }    
}  
}


//locks doors if lock door variable is set
def lockDoors(){
if(allOk){
	if (lockAuto == "Yes"){
		locks?.lock()
        if(sendPushUnsecure != "No") {
    		log.debug("Sending push message")
    		if (!phone || pushAndPhone != "No") {
				log.debug "sending push"
				sendPush("${openLocks.join(', ')} now locked.  You're welcome.  Enjoy your day.")
			}
			if (phone) {
				log.debug "sending SMS"
				sendSms(phone, "${openLocks.join(', ')} now locked.  You're welcome.  Enjoy your day.")
			}
        }    
  	}
}    
}

//send push/phone if all is secure
private sendSecure(msg) {
log.debug("checking push")
  if(sendPushSecure != "No"){
    if (!phone || pushAndPhone != "No") {
		log.debug "sending push"
		sendPush(msg)
	}
	if (phone) {
		log.debug "sending SMS"
		sendSms(phone, msg)
	}
  }
  
  else {
  log.debug("Home is secure but settings don't require push")
}
  log.debug(msg)
}

//send push/phone is unsecure
private sendUnsecure(msg) {
log.debug("checking push")
  if(sendPushUnsecure != "No") {
    log.debug("Sending push message")
    if (!phone || pushAndPhone != "No") {
		log.debug "sending push"
		sendPush(msg)
	}
	if (phone) {
		log.debug "sending SMS"
		sendSms(phone, msg)
	}
  }
  else {
  log.debug("Home is unseecure but settings don't require push")
  }
  log.debug(msg)
}

private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	log.trace "modeOk = $result"
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	log.trace "daysOk = $result"
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	log.trace "timeOk = $result"
	result
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private getTimeIntervalLabel()
{
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}

private hideOptionsSection() {
	(starting || ending || days || modes) ? false : true
}











