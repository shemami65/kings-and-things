package common.event.notifications;


public class ErrorNotification extends Notification {
	private final String message;
	
	public ErrorNotification( String message){
		this.message = message;
	}
	
	public String getMessage(){
		return message;
	}
}