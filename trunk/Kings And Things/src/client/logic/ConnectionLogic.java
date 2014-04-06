package client.logic;

import java.io.IOException;

import common.Logger;
import common.game.PlayerInfo;
import common.network.Connection;
import common.Constants;
import common.Constants.UpdateKey;
import common.Constants.UpdateInstruction;
import common.event.AbstractEvent;
import common.event.UpdatePackage;
import common.event.AbstractUpdateReceiver;
import common.event.network.Flip;
import common.event.network.DieRoll;
import common.event.network.InitiateCombat;
import common.event.network.PlayerTargetChanged;
import common.event.network.StartGame;
import common.event.network.PlayerState;
import common.event.network.PlayersList;
import common.event.network.CurrentPhase;
import common.event.network.HexPlacement;
import common.event.network.CommandRejected;
import common.event.network.ExchangedSeaHex;
import common.event.network.GameStateProgress;
import common.event.network.SpecialCharUpdate;
import common.event.network.HexOwnershipChanged;

public class ConnectionLogic implements Runnable {

	private Connection connection;
	private PlayerInfo player = null;
	private boolean finished = false, gameStarted = false;
	
	public ConnectionLogic() {
		this.connection = new Connection();
		new UpdateReceiver();
		new UpdateTransmitter();
	}

	@Override
	public void run() {
		AbstractEvent event = null;
		Logger.getStandardLogger().info( "Starting");
		while( !finished && !connection.isConnected()){
			try {
				Thread.sleep( 10);
			} catch ( InterruptedException e) {
				e.printStackTrace();
			}
		}
		Logger.getStandardLogger().info( "listening");
		UpdatePackage update = new UpdatePackage("Logic.Run", this);
		int ID = Constants.PUBLIC;
		try {
			while( !finished && (event = connection.recieve())!=null){
				update.clear();
				ID = player==null ? Constants.PUBLIC: player.getID()|Constants.BOARD;
				Logger.getStandardLogger().info( "Received "+(player!=null?player.getID():"-1") + ": " + event);
				if( event instanceof PlayersList){
					PlayerInfo[] players = ((PlayersList)event).getPlayers();
					updateCurrentPlayer( players);
					update.addInstruction( UpdateInstruction.UpdatePlayers);
					updatePlayerFromList(players);
					update.putData( UpdateKey.Player, player);
					update.putData( UpdateKey.Players, players);
					if( !gameStarted){
						ID = Constants.PUBLIC;
					}else{
						ID |= Constants.GUI;
					}
				} 
				else if( event instanceof StartGame){
					update.addInstruction( UpdateInstruction.Start);
					update.putData( UpdateKey.PlayerCount, ((StartGame)event).getPlayerCount());
					gameStarted = true;
					ID = Constants.PUBLIC;//public event
				} 
				else if( event instanceof PlayerState){
					//first data from server, with PlayerInfo object
					player = ((PlayerState)event).getPlayer();
					Thread.currentThread().setName( "Client " + player.getID() + " Logic");
					update.setSource( "Logic.Run "+player.getID());
				}
				else if( event instanceof HexPlacement){
					update.addInstruction( UpdateInstruction.PlaceBoard);
					update.putData( UpdateKey.Hex, ((HexPlacement)event).getHexes());
				}
				else if( event instanceof SpecialCharUpdate){
					update.addInstruction( UpdateInstruction.Special);
					update.putData( UpdateKey.Special, ((SpecialCharUpdate)event).getSpecial());
				}
				else if( event instanceof CurrentPhase){
					CurrentPhase<?> phase = (CurrentPhase<?>) event;
					updateCurrentPlayer( phase.getPlayers());
					update.addInstruction( UpdateInstruction.UpdatePlayers);
					updatePlayerFromList(phase.getPlayers());
					update.putData( UpdateKey.Player, player);
					update.putData( UpdateKey.Players, phase.getPlayers());
					ID |= Constants.GUI;
					if( phase.isSetupPhase()){
						update.addInstruction( UpdateInstruction.SetupPhase);
						update.putData( UpdateKey.Phase, phase.getPhase());
					}else if( phase.isRegularPhase()){
						update.addInstruction( UpdateInstruction.RegularPhase);
						update.putData( UpdateKey.Phase, phase.getPhase());
					}else if( phase.isCombatPhase()){
						event.postInternalEvent(ID |= Constants.COMBAT_PANEL_ID);
					}
				}
				else if( event instanceof DieRoll){
					
					DieRoll evt = (DieRoll)event;
					switch(evt.getDieRoll().getRollReason())
					{
						case ATTACK_WITH_CREATURE:
						case CALCULATE_DAMAGE_TO_TILE:
							evt.postInternalEvent(evt.getDieRoll().getRollingPlayerID() | Constants.COMBAT_PANEL_ID);
							break;
						default:
							update.addInstruction( UpdateInstruction.DieValue);
							update.putData( UpdateKey.Roll, evt.getDieRoll());
							break;
					}
				}
				else if(event instanceof HexOwnershipChanged){
					update.addInstruction( UpdateInstruction.HexOwnership);
					update.putData( UpdateKey.HexState, ((HexOwnershipChanged)event).getChangedHex());
				}
				else if( event instanceof Flip){
					update.addInstruction( UpdateInstruction.FlipAll);
				}
				else if( event instanceof ExchangedSeaHex){
					update.addInstruction( UpdateInstruction.SeaHexChanged);
					update.putData( UpdateKey.HexState, ((ExchangedSeaHex)event).getSate());
				}
				else if( event instanceof GameStateProgress){
					GameStateProgress progress = (GameStateProgress)event;
					updateCurrentPlayer( progress.getPlayers());
					update.addInstruction( UpdateInstruction.UpdatePlayers);

					updatePlayerFromList(progress.getPlayers());
					update.putData( UpdateKey.Player, player);
					update.putData( UpdateKey.Players, progress.getPlayers());
					update.addInstruction( UpdateInstruction.GameState);
					update.putData( UpdateKey.Flipped, progress.isFlipped());
					update.putData( UpdateKey.Setup, progress.getSetup());
					update.putData( UpdateKey.Regular, progress.getRegular());
					update.putData( UpdateKey.Combat, progress.getCombat());
					update.putData( UpdateKey.Hex, progress.getHexes( -1));
					update.putData( UpdateKey.Special, progress.getSpecial( -1));
					update.putData( UpdateKey.Rack, progress.getRack( player.getID()));
				}
				else if( event instanceof CommandRejected){
					UpdateInstruction instruction = ((CommandRejected)event).getInstruction(); 
					if(instruction==null){
						return;
					}
					update.addInstruction( UpdateInstruction.Rejected);
					switch( instruction){
						case SeaHexChanged:
							update.putData(UpdateKey.Message, ((CommandRejected)event).getErrorMessage());
						case TieRoll:
						case HexOwnership:
						case Skip:
							update.putData(UpdateKey.Instruction, instruction);
							break;
						default:
							throw new IllegalStateException("Logic.Receive " + (player!=null?player.getID():"-1") + ": No Support for rejection: " + instruction);
					}
				}
				else if(event instanceof InitiateCombat)
				{
					update.addInstruction(UpdateInstruction.InitiateCombat);
					update.putData(UpdateKey.Combat, event);
				}
				else if(event instanceof PlayerTargetChanged)
				{
					PlayerTargetChanged evt = ((PlayerTargetChanged)event);
					evt.postInternalEvent(evt.getTargettingPlayer().getID() | Constants.COMBAT_PANEL_ID);
				}
				else {
					Logger.getStandardLogger().warn( "\tNO Handel for: " + event);
					throw new IllegalStateException("NO handle for: " + event);
				}
				/*
				else if( event instanceof PlayerOrderList){
					new BoardUpdate(((PlayerOrderList)event).getList(), this).postInternalEvent(BOARD|player.getID());
				}
				else if( event instanceof RackPlacement){
					new BoardUpdate(((RackPlacement)event).getArray(), this).postInternalEvent(BOARD|player.getID());
				}
				else if(event instanceof HexStatesChanged){
					
				}*/
				
				if( update.isModified()){
					update.postInternalEvent( ID);
				}
			}
		} catch ( ClassNotFoundException | IOException e) {
			Logger.getStandardLogger().warn( e);
		}
		finished = true;
		Logger.getStandardLogger().warn( "logic disconnected");
	}
	
	private void updateCurrentPlayer( PlayerInfo[] players){
		for( PlayerInfo player:players){
			if( player!=null && this.player.equals( player)){
				this.player = player;
				break;
			}
		}
	}
	
	private class UpdateReceiver extends AbstractUpdateReceiver<UpdatePackage>{

		protected UpdateReceiver() {
			super( INTERNAL, Constants.LOGIC, ConnectionLogic.this);
		}

		@Override
		protected void handlePublic( UpdatePackage update) {
			if( update.peekFirstInstruction()==UpdateInstruction.End){
				if( connection!=null){
					connection.disconnect();
				}
				finished = true;
			}
		}

		@Override
		protected void handlePrivate( UpdatePackage update) {
			UpdateInstruction[] instructions = update.getInstructions();
			for( UpdateInstruction instruction : instructions){
				process( instruction, update);
			}
		}
		
		@Override
		protected boolean verifyPrivate( UpdatePackage update){
			return update.isValidID(ID) || update.isValidID(player);
		}
	}
	
	private void process( UpdateInstruction instruction, UpdatePackage data) {
		UpdateInstruction netaction = UpdateInstruction.Disconnect;
		String message = "Unable To Connect, Try Again";
		switch( instruction){
			case Connect:
				String name = (String)data.getData( UpdateKey.Name);
				String ip = (String)data.getData( UpdateKey.IP);
				int port = (Integer)data.getData( UpdateKey.Port);
				if( name==null || name.length()<=0){
					message += "\nThere Must Be a Name";
				}else{
					if( name.matches("(-demo)([\\s](\\w+)){2,4}")){
						String[] names = name.split( " ");
						ConnectionLogic logic;
						for( int i=1; i<names.length-1; i++){
							try{
								logic = new ConnectionLogic();
								netaction = logic.connect( ip, port, names[i]);
								if( netaction==UpdateInstruction.Disconnect){
									break;
								}
								startLogic( logic);
							}catch(IllegalArgumentException | IOException ex){
								message += "\n" + ex.getMessage();
							}
						}
						name = names[names.length-1];
					}else if( name.startsWith("-demo")){
						message += "\n\"-demo\" must follow with 2-4 unique names";
					}
					try{
						netaction = connect( ip, port, name);
					}catch(IllegalArgumentException | IOException ex){
						message += "\n" + ex.getMessage();
					}
				}
				break;
			case Disconnect:
				if( connection!=null){
					connection.disconnect();
				}
				message = "Disconnect";
				break;
			case State:
				netaction = UpdateInstruction.State;
				player.setReady( !player.isReady());
				message = !player.isReady()? "Ready":"UnReady";
				sendToServer( new UpdatePackage( UpdateInstruction.State, UpdateKey.Player, player, "Logic "+player.getID()));
				break;
			default:
				throw new IllegalArgumentException( "No handle for instruction: " + instruction);
		}
		UpdatePackage update = new UpdatePackage("Logic.Process.Receive "+(player!=null?player.getID()+" ":""), this);
		update.addInstruction( netaction);
		update.putData( UpdateKey.Message, message);
		update.putData( UpdateKey.PlayerCount, 0);
		update.postInternalEvent();
	}

	private UpdateInstruction connect(String ip, int port, String name) throws IllegalArgumentException, IOException{
		Logger.getStandardLogger().info( "Connecting");
		if( connection.connectTo( ip, port)){
			Logger.getStandardLogger().info( "Connected");
			if( finished){
				Logger.getStandardLogger().info( "Starting Thread");
				finished = false;
				startLogic( this);
			}
			if( player!=null){
				Logger.getStandardLogger().info( "Send Old Player");
				sendToServer( new UpdatePackage( UpdateInstruction.State, UpdateKey.Player, player, "Logic "+player.getID()));
			}else{
				Logger.getStandardLogger().info( "Send New Player");
				sendToServer( new UpdatePackage( UpdateInstruction.State, UpdateKey.Player, new PlayerInfo( name, Constants.PUBLIC, Constants.PLAYER_READY), "Logic -1"));
			}
			return UpdateInstruction.Connect;
		}
		Logger.getStandardLogger().info( "Failed Connecting");
		return UpdateInstruction.Disconnect;
	}
	
	private void startLogic( ConnectionLogic logic){
		new Thread( logic, "Client Logic").start();
	}
	
	private class UpdateTransmitter extends AbstractUpdateReceiver<UpdatePackage>{

		protected UpdateTransmitter() {
			super( NETWORK, Constants.LOGIC, ConnectionLogic.this);
		}

		@Override
		protected void handlePrivate( UpdatePackage update) {
			sendToServer( update);
		}

		@Override
		protected boolean verifyPrivate( UpdatePackage update) {
			return update.isValidID(ID) || update.isValidID(player);
		}
	}

	public void sendToServer( UpdatePackage event){
		Logger.getStandardLogger().info( "Sent" + (player!=null?player.getID():"-1") + ": " + event);
		try {
			connection.send( event);
		} catch ( IOException e) {
			Logger.getStandardLogger().warn( e);
		}
	}
	
	private void updatePlayerFromList(PlayerInfo[] playerList)
	{
		for(PlayerInfo pi : playerList)
		{
			if(pi.getID() == player.getID())
			{
				player = pi;
				break;
			}
		}
	}
}
