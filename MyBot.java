import hlt.*;

import java.util.*;

public class MyBot {

	static ArrayList<Move> moveList = new ArrayList<>();
	static Map<Integer, Integer> currentProduction = new HashMap();
	static Map<Integer, Integer> currentStrength = new HashMap();
	static Map<Integer, Integer> currentTerritory = new HashMap();
	static Map<Double, Entity> entityNearShip;
	static List<Planet> planetNearShipList;
	static List<Ship> shipsNearShipList;
	static int turn;
	static final Networking networking = new Networking();
	static final GameMap gameMap = networking.initialize("Tamagocchi");
	static final Player player = gameMap.getMyPlayer();
	static final int playerId = player.getId();

	public static void main(final String[] args) {

		// We now have 1 full minute to analyse the initial map.
		final String initialMapIntelligence = "width: " + gameMap.getWidth() + "; height: " + gameMap.getHeight()
				+ "; players: " + gameMap.getAllPlayers().size() + "; planets: " + gameMap.getAllPlanets().size();
		Log.log(initialMapIntelligence);

		while (true) {

			turn++;
			currentProduction.clear();
			currentStrength.clear();
			currentTerritory.clear();
			moveList.clear();
			networking.updateMap(gameMap);

			sweepMap();

			for (final Ship ship : gameMap.getMyPlayer().getShips().values()) {

				entityNearShip = gameMap.nearbyEntitiesByDistance(ship);
				planetNearShipList = getNearestPlanet(entityNearShip);
				shipsNearShipList = getNearestShip(entityNearShip);

				if (ship.getDockingStatus() != Ship.DockingStatus.Undocked) {
					ship.setTargetPlanet(100);
					continue;
				}

				if (expandTerritory(ship)) {
					continue;
				}
				if (attack(ship)) {
					continue;
				}

			} // end for each nearest planet

			Networking.sendMoves(moveList);

		}

	}

	// expands territory by finding nearest unoccupied or friendly planet to the
	// ship. needs to change to planet near current one
	public static boolean expandTerritory(Ship ship) {
		for (int i = 0; i < planetNearShipList.size(); i++) {

			// Move to nearest planet and dock
			if (!planetNearShipList.get(i).isFull()) {

				// check for # of ships heading for this planet
				if (ship.canDock(planetNearShipList.get(i))
						&& (!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)) {
					moveList.add(new DockMove(ship, planetNearShipList.get(i)));
					return true;
				}

				// move to most profitable planet at beginning of match
				if (!currentTerritory.containsKey(playerId)) {
					if ((!getMostProductivePlanet(ship).isOwned()) && ship.canDock(getMostProductivePlanet(ship))) {
						final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
								getMostProductivePlanet(ship), Constants.MAX_SPEED / 2);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
							ship.setTargetPlanet(getMostProductivePlanet(ship).getId());

						}
						return true;
					} else if (!getMostProductivePlanet(ship).isOwned()) {
						final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
								getMostProductivePlanet(ship), Constants.MAX_SPEED);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
							ship.setTargetPlanet(getMostProductivePlanet(ship).getId());

						}
						return true;
					}
				}

				// if num ships headed for nearest planet > docking spots, continue
				int numShip = 0;
				for (Ship s : shipsNearShipList) {
					if (s.getOwner() == playerId && s.getTargetPlanet() == ship.getTargetPlanet()) {
						numShip++;
					}
				}
				if (numShip > planetNearShipList.get(i).getDockingSpots() && !planetNearShipList.get(i).isOwned()) {
					continue;
				}

				// move to nearest player's planet or unowned
				if ((!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)
						&& ship.canDock(planetNearShipList.get(i)) && !enemyWithinTerritory(ship)) {
					final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
							planetNearShipList.get(i), Constants.MAX_SPEED / 2);
					if (newThrustMove != null) {
						moveList.add(newThrustMove);
						ship.setTargetPlanet(planetNearShipList.get(i).getId());

					}
					return true;
				} else if ((!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)
						&& !enemyWithinTerritory(ship)) {
					final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
							planetNearShipList.get(i), Constants.MAX_SPEED);
					if (newThrustMove != null) {
						moveList.add(newThrustMove);
						ship.setTargetPlanet(planetNearShipList.get(i).getId());

					}
					return true;
				} else if (planetNearShipList.get(i).getOwner() != playerId) {
					return false;
				}

			}
		}
		return false;
	}

	public static boolean attack(Ship ship) {
		for (int i = 0; i < planetNearShipList.size(); i++) {
			// Attack Plan
			if (planetNearShipList.get(i).getOwner() != playerId) {

				// If nearest entity is enemy ship, intercept it else go to nearest enemy ship
				// near planet

				for (Ship s : shipsNearShipList) {

					// enemy ship closer than nearest planet
					if (s.getOwner() != playerId
							&& ship.getDistanceTo(s) < ship.getDistanceTo(planetNearShipList.get(0))) {
						final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
								Constants.MAX_SPEED);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
						}

						return true;
					}
					// planet closer than enemy ship and is occupied
					if (s.getOwner() != playerId) {
						final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
								Constants.MAX_SPEED);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
						}

						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean enemyWithinTerritory(Ship ship) {
		for (Planet p : gameMap.getAllPlanets().values()) {
			if (p.getOwner() == playerId) {
				for (Entity e : gameMap.objectsBetween(ship, p)) {
					// enemy ship closer than controlled planet
					if (e instanceof Ship && ((Ship) e).getOwner() != playerId) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static void sweepMap() {
		for (Planet planet : gameMap.getAllPlanets().values()) {
			// Compute player stats
			if (!planet.isOwned()) {
				if (currentProduction.containsKey(planet.getOwner())) {
					currentProduction.put(planet.getOwner(),
							currentProduction.get(planet.getOwner()) + planet.getCurrentProduction());
					currentStrength.put(planet.getOwner(), currentStrength.get(planet.getOwner()) + planet.getHealth());
					currentTerritory.put(planet.getOwner(), currentTerritory.get(planet.getOwner()) + 1);
				} else {
					currentProduction.put(planet.getOwner(), planet.getCurrentProduction());
					currentStrength.put(planet.getOwner(), planet.getHealth());
					currentTerritory.put(planet.getOwner(), 1);
				}
			}

		}
	}

	public static List<Planet> getNearestPlanet(Map<Double, Entity> map) {
		List<Planet> planet = new ArrayList<Planet>();
		for (Map.Entry<Double, Entity> e : map.entrySet()) {
			if (e.getValue() instanceof Planet) {
				planet.add((Planet) e.getValue());
			}
		}
		return planet;
	}

	public static List<Ship> getNearestShip(Map<Double, Entity> map) {
		List<Ship> ship = new ArrayList<Ship>();
		for (Map.Entry<Double, Entity> e : map.entrySet()) {
			if (e.getValue() instanceof Ship) {
				ship.add((Ship) e.getValue());
			}
		}
		return ship;
	}

	public static Planet getMostProductivePlanet(Ship ship) {
		double oneProfit = planetNearShipList.get(0).getDockingSpots() / ship.getDistanceTo(planetNearShipList.get(0));
		double twoProfit = planetNearShipList.get(1).getDockingSpots() / ship.getDistanceTo(planetNearShipList.get(1));
		double threeProfit = planetNearShipList.get(2).getDockingSpots()
				/ ship.getDistanceTo(planetNearShipList.get(2));

		if (threeProfit > twoProfit && threeProfit > oneProfit) {
			return planetNearShipList.get(2);
		}
		if (twoProfit > threeProfit && twoProfit > oneProfit) {
			return planetNearShipList.get(1);
		}
		return planetNearShipList.get(0);
	}

}
