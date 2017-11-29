import hlt.*;

import java.util.*;

public class MyBot {

	static ArrayList<Move> moveList = new ArrayList<>();
	static Map<Integer, Integer> currentProduction = new HashMap<Integer, Integer>();
	static Map<Integer, Integer> currentStrength = new HashMap<Integer, Integer>();
	static Map<Integer, Integer> currentTerritory = new HashMap<Integer, Integer>();
	// entities near ship
	static Map<Double, Entity> entityNearShip;
	// list of planets near ship
	static List<Planet> planetNearShipList;
	// list of planets near curr planet
	static List<Planet> planetNearPlanetList;
	// list of ships near ship
	static List<Ship> shipsNearShipList;
	static int turn;
	static final Networking networking = new Networking();
	static final GameMap gameMap = networking.initialize("lee-aaron");
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
				
				// different strategy for 2 players
				// send *closest* ship to attack their docked ships and avoid undocked ones and rest should dock
				if(gameMap.getAllPlayers().size() == 2) {
					if(gameMap.getMyPlayer().getShips().get(0) != null && gameMap.getMyPlayer().getShips().get(0).getId() == ship.getId()) {
						for(Ship s : shipsNearShipList) {
							if(s.getOwner() != playerId && s.getDockingStatus() != Ship.DockingStatus.Undocked) {
								final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
										Constants.MAX_SPEED);
								if (newThrustMove != null) {
									moveList.add(newThrustMove);
									ship.setTargetShip(s.getId());
									Log.log("Ship Attack: " + String.valueOf(ship.getId()) + " "
											+ String.valueOf(ship.getTargetShip()) + " " + String.valueOf(getShipHFShip(s)));
								}
								break;
							} else if(s.getOwner() != playerId) {
								final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
										Constants.MAX_SPEED);
								if (newThrustMove != null) {
									moveList.add(newThrustMove);
									ship.setTargetShip(s.getId());
									Log.log("Ship Attack: " + String.valueOf(ship.getId()) + " "
											+ String.valueOf(ship.getTargetShip()) + " " + String.valueOf(getShipHFShip(s)));
								}
								break;
							}
						}
						continue;
					} else {
						if (ship.getDockingStatus() != Ship.DockingStatus.Undocked) {
							continue;
						}
						if(expandTerritory(ship)) {
							continue;
						}
						if(attack(ship)) {
							continue;
						}
						if(explore(ship)) {
							continue;
						}
					}
					
				}

				// better handle if all ships are docked and enemy ship is coming
				// for each planet i own if nearest planet to curr one is enemy attack else
				// expand

				if (ship.getDockingStatus() != Ship.DockingStatus.Undocked) {
					continue;
				}
				if (expandTerritory(ship)) {
					continue;
				}
				if (attack(ship)) {
					continue;
				}
				if(explore(ship)) {
					continue;
				}

			} // end for each nearest planet

			Networking.sendMoves(moveList);

		}

	}

	// expands territory by finding nearest unoccupied or friendly planet to the
	// ship. needs to change to nearest planet to planet. if occupied by enemy,
	// attack else
	// dock
	public static boolean expandTerritory(Ship ship) {

		for (int i = 0; i < planetNearShipList.size(); i++) {

			List<Planet> nearestUnownedPlanet = getNearestUnownedPlanet(planetNearShipList.get(i));

			// if num ships headed for nearest planet >= docking spots, continue
			int numShip = 1;
			for (Ship s : shipsNearShipList) {
				if (s.getOwner() == playerId && s.getTargetPlanet() == planetNearShipList.get(i).getId()) {
					numShip++;
				}
			}
			if (numShip >= planetNearShipList.get(i).getDockingSpots()
					&& (!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)) {
				continue;
			}

			// checks if enemy planet is closer
			if (planetNearShipList.get(i).isOwned() && planetNearShipList.get(i).getOwner() != playerId
					&& nearestUnownedPlanet.size() != 0
					&& (ship.getDistanceTo(planetNearShipList.get(i)) <= ship.getDistanceTo(nearestUnownedPlanet.get(0))
							|| enemyWithinTerritory(ship))) {
				Log.log("Attacking Planet: " + String.valueOf(ship.getId()) + " "
						+ String.valueOf(planetNearShipList.get(i).getId()));
				return false;
				// checks if nearest enemy planet is closer than nearest friendly planet
			} else if (planetNearShipList.get(i).isOwned() && planetNearShipList.get(i).getOwner() != playerId
					|| enemyWithinTerritory(ship)) {
				Log.log("Attacking Planet: " + String.valueOf(ship.getId()) + " "
						+ String.valueOf(planetNearShipList.get(i).getId()));
				return false;
			}

			// Move to nearest planet and dock
			if (!planetNearShipList.get(i).isFull()) {

				if (ship.canDock(planetNearShipList.get(i))
						&& (!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)) {
					moveList.add(new DockMove(ship, planetNearShipList.get(i)));
					Log.log("Docking: " + String.valueOf(ship.getId()) + " "
							+ String.valueOf(planetNearShipList.get(i).getId()));
					return true;
				}

				// move to most profitable planet at beginning of match
				// implement position avoiding
				if (!currentTerritory.containsKey(playerId)) {

					// checks if nearest unowned planet has spots and ship can dock
					if ((!getMostProductivePlanet(ship).isOwned()) && ship.getDistanceTo(getMostProductivePlanet(ship)) <= 10
							&& numShip < getMostProductivePlanet(ship).getDockingSpots()) {
						ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
								getMostProductivePlanet(ship), Constants.MAX_SPEED / 2);
						if (newThrustMove != null) {
							ship.setTargetPosition(ship.getClosestPoint((getMostProductivePlanet(ship))));
							ship.setTargetPlanet(getMostProductivePlanet(ship).getId());
							if (!Collision.segmentCircleIntersect(new Position(ship.getXPos(), ship.getYPos()),
									ship.getTargetPosition(), shipsNearShipList.get(0),
									Constants.FORECAST_FUDGE_FACTOR)) {
								moveList.add(newThrustMove);
							} else {
								newThrustMove = Navigation.navigateShipToAvoid(gameMap, ship,
										getMostProductivePlanet(ship), Constants.MAX_SPEED / 2);
								if (newThrustMove != null) {
									moveList.add(newThrustMove);
									Log.log("Collision Avoided: " + String.valueOf(ship.getId()) + " "
											+ String.valueOf(getMostProductivePlanet(ship).getId()));
								}
							}

						}

						return true;
						// nearest unowned planet has spots
					} else if (!getMostProductivePlanet(ship).isOwned()
							&& numShip < getMostProductivePlanet(ship).getDockingSpots()) {
						ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
								getMostProductivePlanet(ship), Constants.MAX_SPEED);
						if (newThrustMove != null) {
							ship.setTargetPosition(ship.getClosestPoint((getMostProductivePlanet(ship))));
							ship.setTargetPlanet(getMostProductivePlanet(ship).getId());
							if (!Collision.segmentCircleIntersect(new Position(ship.getXPos(), ship.getYPos()),
									ship.getTargetPosition(), shipsNearShipList.get(0),
									Constants.FORECAST_FUDGE_FACTOR)) {
								moveList.add(newThrustMove);
							} else {
								newThrustMove = Navigation.navigateShipToAvoid(gameMap, ship,
										getMostProductivePlanet(ship), Constants.MAX_SPEED);
								if (newThrustMove != null) {
									moveList.add(newThrustMove);
									Log.log("Collision Avoided: " + String.valueOf(ship.getId()) + " "
											+ String.valueOf(getMostProductivePlanet(ship).getId()));
								}
							}

						}
						return true;
					}
				}

				// move to nearest player's planet or unowned and no enemy ship
				// needs to expand to nearest unowned planet next to owned planets
				if ((!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)
						&& ship.getDistanceTo(planetNearShipList.get(i)) <= 10
						&& numShip < planetNearShipList.get(i).getDockingSpots()) {
					final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
							planetNearShipList.get(i), Constants.MAX_SPEED / 2);
					if (newThrustMove != null) {
						moveList.add(newThrustMove);
						ship.setTargetPlanet(planetNearShipList.get(i).getId());
						Log.log("Target: " + String.valueOf(ship.getId()) + " "
								+ String.valueOf(planetNearShipList.get(i).getId()));

					}
					return true;
				} else if ((!planetNearShipList.get(i).isOwned() || planetNearShipList.get(i).getOwner() == playerId)
						&& numShip < planetNearShipList.get(i).getDockingSpots()) {
					final ThrustMove newThrustMove = Navigation.navigateShipToDock(gameMap, ship,
							planetNearShipList.get(i), Constants.MAX_SPEED);
					if (newThrustMove != null) {
						moveList.add(newThrustMove);
						ship.setTargetPlanet(planetNearShipList.get(i).getId());
						Log.log("Target: " + String.valueOf(ship.getId()) + " "
								+ String.valueOf(planetNearShipList.get(i).getId()));

					}
					return true;

				}
			}
		}
		return false;
	}

	// change attack plan to account for health and dmg. also predict target ship
	// next position and track # of ships attacking another one
	public static boolean attack(Ship ship) {
		for (int i = 0; i < planetNearShipList.size(); i++) {
			// Attack Plan
			if (planetNearShipList.get(i).getOwner() != playerId && planetNearShipList.get(i).isOwned()) {

				// If nearest entity is enemy ship, intercept it else go to nearest enemy ship
				// near planet

				for (Ship s : shipsNearShipList) {

					// enemy ship closer than nearest planet
					if (s.getOwner() != playerId
							&& ship.getDistanceTo(s) <= ship.getDistanceTo(planetNearShipList.get(0))
							&& getShipHFShip(s) < 5) {
						final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
								Constants.MAX_SPEED);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
							ship.setTargetShip(s.getId());
							Log.log("Ship Attack: " + String.valueOf(ship.getId()) + " "
									+ String.valueOf(ship.getTargetShip()) + " " + String.valueOf(getShipHFShip(s)));
						}

						return true;
					}
					// planet closer than enemy ship and is occupied
					if (s.getOwner() != playerId && getShipHFShip(s) < 3) {
						final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s,
								Constants.MAX_SPEED);
						if (newThrustMove != null) {
							moveList.add(newThrustMove);
							ship.setTargetShip(s.getId());
							Log.log("Ship Attack: " + String.valueOf(ship.getId()) + " "
									+ String.valueOf(ship.getTargetShip()) + " " + String.valueOf(getShipHFShip(s)));
						}

						return true;
					}

				}
			}

		}
		Log.log("Not attacking: " + String.valueOf(ship.getId()));
		return false;
	}

	// find all ships and if not owner then intercept
	public static boolean explore(Ship ship) {
		for (Ship s : gameMap.getAllShips()) {
			if (s.getOwner() != playerId) {
				final ThrustMove newThrustMove = Navigation.navigateShipToAttack(gameMap, ship, s, Constants.MAX_SPEED);
				if (newThrustMove != null) {
					moveList.add(newThrustMove);
					ship.setTargetShip(s.getId());
					Log.log("Ship Attack: " + String.valueOf(ship.getId()) + " " + String.valueOf(ship.getTargetShip())
							+ " " + String.valueOf(getShipHFShip(s)));
				}

				return true;
			}
		}
		return false;
	}

	// detection if enemies are coming near planet.
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
			if (!p.isOwned()) {
				for (Entity e : gameMap.nearbyEntitiesByDistance(p).values()) {
					if (e instanceof Ship && ((Ship) e).getOwner() != playerId && ((Ship) e).getDistanceTo(p) <= 10
							&& ship.getDistanceTo(((Ship) e)) >= ship.getDistanceTo(p)) {
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

	// returns a list of planets near the ship
	public static List<Planet> getNearestPlanet(Map<Double, Entity> map) {
		List<Planet> planet = new ArrayList<Planet>();
		for (Map.Entry<Double, Entity> e : map.entrySet()) {
			if (e.getValue() instanceof Planet) {
				planet.add((Planet) e.getValue());
			}
		}
		return planet;
	}

	public static List<Planet> getNearestUnownedPlanet(Planet p) {
		Map<Double, Entity> map = gameMap.nearbyEntitiesByDistance(p);
		List<Planet> planet = new ArrayList<Planet>();
		for (Map.Entry<Double, Entity> e : map.entrySet()) {
			if (e.getValue() instanceof Planet && !((Planet) e.getValue()).isOwned()) {
				planet.add((Planet) e.getValue());
			}
		}
		return planet;
	}

	public static List<Planet> getNearestEnemyPlanet(Planet p) {
		Map<Double, Entity> map = gameMap.nearbyEntitiesByDistance(p);
		List<Planet> planet = new ArrayList<Planet>();
		for (Map.Entry<Double, Entity> e : map.entrySet()) {
			if (e.getValue() instanceof Planet && ((Planet) e.getValue()).getOwner() != playerId) {
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

	public static int getShipHFShip(Ship target) {
		int count = 0;
		for (Ship s : gameMap.getMyPlayer().getShips().values()) {
			if (s.getTargetShip() == target.getId()) {
				count++;
			}
		}
		return count;
	}

}
