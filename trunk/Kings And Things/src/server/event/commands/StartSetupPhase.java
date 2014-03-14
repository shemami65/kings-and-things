package server.event.commands;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import common.event.AbstractInternalEvent;

import server.logic.game.Player;

public class StartSetupPhase extends AbstractInternalEvent{

	private final boolean demoMode;
	
	private final HashSet<Player> players;
	
	public StartSetupPhase(boolean demoMode, Set<Player> players, final Object OWNER){
		super( OWNER);
		this.demoMode = demoMode;
		this.players = new HashSet<Player>();
		for(Player p : players)
		{
			this.players.add(p);
		}
	}
	
	public Set<Player> getPlayers()
	{
		return Collections.unmodifiableSet(players);
	}
	
	public boolean getDemoMode()
	{
		return demoMode;
	}
}