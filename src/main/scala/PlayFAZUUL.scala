package com.JeffH001

import java.sql.DriverManager
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.ResultSet
import scala.util.Random
import scala.util.matching.Regex
import scala.io.Source.fromFile
import scala.io.StdIn.readLine
import scala.io.AnsiColor._

// FYI - If you're running your own copy of this, look for code changes you may need to make on the lines marked with "// ToDo:"

object Main {
	val random = new Random
	val CLEARSCR = "\u001b[2J"
	val BACKLN = "\u001b[1F\u001b[2K"
	var db:java.sql.Statement = null
	var playername = ""
	var playerlocation = 1
	var itemlist = Array.empty[String]
	var itemcombinations = 0

	// Utility functions:

	/**
	  * Makes sure that the string passed in only contains English characters, numbers, and has no more than "count" space-separated parameters.
	  * Also converts any plural item names to their singular version.
	  *
	  * @param params	- The string to be checked.
	  * @param count	- The maximum amount of space-separated parameters allowed.  If less than 1 then accept all parameters.
	  * @return			- The validated string.
	  */
	def validate(params: String, count: Int):String = {
		var output = params.replaceAll("[^a-zA-Z0-9 ]+","");
		if (count < 1)
			output
		else {
			var result = ""
			var arg = ""
			var i = 0
			var n = count
			while ((n > 0) && (output != "")) {  // Limit to "count" number of parameters
				if (output.indexOf(" ") > 0) {
					if (result.length() > 0)
						result += " "
					arg = output.substring(0, output.indexOf(" "))
					i = itemlist.indexOf(arg.toLowerCase())
					if (i > 0) {
						if (i % 2 == 1)
							arg = itemlist(i - 1)  // Substitute plural for singular version
					}
					result += arg
					output = output.substring(output.indexOf(" ") + 1).trim()
				} else {  // Last parameter
					i = itemlist.indexOf(output.toLowerCase())
					if (i > 0) {
						if (i % 2 == 1)
							output = itemlist(i - 1)  // Substitute plural for singular version
					}
					if (result.length() > 0)
						result += " " + output
					else
						result = output
					output = ""
				}
				n -= 1
			}
			result
		}
	}

	/**
	  * Returns how many of an item the player has in their inventory.
	  *
	  * @param item - The item name.
	  * @return		- Item quantity.
	  */
	def playerHasItem(item: String):Int = {
		if (itemlist.contains(item.toLowerCase())) {
			val resultSet = db.executeQuery(s"SELECT * FROM inventory WHERE PlayerName = '$playername' AND ItemName = '$item';")
			if (resultSet.next())
				resultSet.getInt("Quantity")
			else
				0
		} else
			0
	}

	/**
	  * Changes the quantity of an item in the player's inventory.
	  *
	  * @param item		- The item name.
	  * @param count	- The amount to modify the quantity by.
	  * @return			- The amount remaining.
	  */
	def playerChangeItem(item: String, count: Int):Int = {
		if (itemlist.contains(item.toLowerCase())) {
			val resultSet = db.executeQuery(s"SELECT * FROM inventory WHERE PlayerName = '$playername' AND ItemName = '$item';")
			var hasquantity = 0
			if (resultSet.next())
				hasquantity = resultSet.getInt("Quantity")
			var newquantity = hasquantity + count
			if (newquantity < 0)
				newquantity = 0
			if ((hasquantity > 0) && (newquantity == 0))  // Remove all of item from inventory
				db.executeUpdate(s"DELETE FROM inventory WHERE PlayerName = '$playername' AND ItemName = '$item';")
			else if ((hasquantity == 0) && (newquantity > 0))  // Add new item to inventory
				db.executeUpdate(s"INSERT INTO inventory (PlayerName, ItemName, Quantity) VALUES ('$playername', '$item', $newquantity);")
			else if ((hasquantity > 0) && (newquantity > 0))  // Change the quantity in the inventory
				db.executeUpdate(s"UPDATE inventory SET Quantity = $newquantity WHERE PlayerName = '$playername' AND ItemName = '$item';")
			newquantity  // Return the new quantity
		} else {
			println(s"${BOLD}${RED}ERROR:${WHITE} Unable to add unknown item '$item' to inventory.${RESET}")
			0
		}
	}

	/**
	  * Returns how many of an item is in the room the player is currently in.
	  *
	  * @param item - The item name.
	  * @return		- Item quantity.
	  */
	def roomHasItem(item: String):Int = {
		if (itemlist.contains(item.toLowerCase())) {
			val resultSet = db.executeQuery(s"SELECT * FROM roomitems WHERE Coordinates = '$playerlocation' AND ItemName = '$item';")
			if (resultSet.next())
				resultSet.getInt("Quantity")
			else
				0
		} else
			0
	}

	/**
	  * Changes the quantity of an item in the current room.
	  *
	  * @param item		- The item name.
	  * @param count	- The amount to modify the quantity by.
	  * @return			- The amount remaining.
	  */
	def roomChangeItem(item: String, count: Int):Int = {
		if (itemlist.contains(item.toLowerCase())) {
			val resultSet = db.executeQuery(s"SELECT * FROM roomitems WHERE Coordinates = '$playerlocation' AND ItemName = '$item';")
			var hasquantity = 0
			if (resultSet.next())
				hasquantity = resultSet.getInt("Quantity")
			var newquantity = hasquantity + count
			if (newquantity < 0)
				newquantity = 0
			if ((hasquantity > 0) && (newquantity == 0))  // Remove all of item from roopm
				db.executeUpdate(s"DELETE FROM roomitems WHERE Coordinates = $playerlocation AND ItemName = '$item';")
			else if ((hasquantity == 0) && (newquantity > 0))  // Add new item to room
				db.executeUpdate(s"INSERT INTO roomitems (Coordinates, ItemName, Quantity) VALUES ($playerlocation, '$item', $newquantity);")
			else if ((hasquantity > 0) && (newquantity > 0))  // Change the quantity in the room
				db.executeUpdate(s"UPDATE roomitems SET Quantity = $newquantity WHERE Coordinates = $playerlocation AND ItemName = '$item';")
			newquantity  // Return the new quantity
		} else {
			println(s"${BOLD}${RED}ERROR:${WHITE} Unable to add unknown item '$item' to room.${RESET}")
			0
		}
	}

	/**
	  * Shows information about the player's current location.
	  *
	  * @param force - If "force" is "true" then it forces showing the location's description.
	  */
	def showLocation(force: Boolean = false):Unit = {
		var resultSet = db.executeQuery(s"SELECT RoomName, Description FROM rooms WHERE Coordinates = $playerlocation;")
		var roomname = ""
		var description = ""
		if (resultSet.next()) {  // Get room information
			roomname = resultSet.getString("RoomName")
			description = resultSet.getString("Description")
		}
		var seenroom = true
		resultSet = db.executeQuery(s"SELECT * FROM seenrooms WHERE Coordinates = $playerlocation AND PlayerName = '$playername';")
		if (resultSet.next() == false) {  // See if the player has seen the room before
			seenroom = false
		}
		// Show the room name
		println(s"\n${BOLD}$roomname${RESET}")
		if (!seenroom || force) {  // Show room's description
			println("\t" + description + "\n")
			if (!seenroom)  // add it to the list of known rooms for this player
				db.executeUpdate(s"INSERT INTO seenrooms (PlayerName, Coordinates) VALUES ('$playername', '$playerlocation');")
		}
		// Show what items are in the room
		resultSet = db.executeQuery(s"SELECT * FROM roomitems WHERE Coordinates = $playerlocation;")
		if (resultSet.next()) {
			print(s"\t${BOLD}Items:${RESET} " + resultSet.getString("ItemName"))
			if (resultSet.getInt("Quantity") > 1) {
				print(" (" + resultSet.getInt("Quantity") + ")")
			}
			while (resultSet.next()) {
				print(", " + resultSet.getString("ItemName"))
				if (resultSet.getInt("Quantity") > 1) {
					print(" (" + resultSet.getInt("Quantity") + ")")
				}
			}
			println("")
		}
		// Show players in the room besides the current player.
		resultSet = db.executeQuery(s"SELECT PlayerName FROM players WHERE Coordinates = $playerlocation AND PlayerName != '$playername';")
		if (resultSet.next()) {
			print(s"\t${BOLD}Players:${RESET} " + resultSet.getString("PlayerName"))
			while (resultSet.next()) {
				print(", " + resultSet.getString("PlayerName"))
			}
			println("")
		}
		// Show what exits are available
		resultSet = db.executeQuery(s"SELECT * FROM roomconnections WHERE FromRoom = $playerlocation;")
		if (resultSet.next()) {
			print(s"\t${BOLD}Exits:${RESET} " + resultSet.getString("DirName"))
			while (resultSet.next()) {
				print(", " + resultSet.getString("DirName"))
			}
			println("")
		}
	}

	// Game commands:

	/**
	  * ADD: Adds items to the player's inventory.
	  *
	  * @param params - The item to add to inventory, possibly preceded by an optional quantity.
	  */
	def addItem(params: String):Unit = {
		var item = ""
		var count = 1
		if (params.indexOf(" ") > 0) {
			item = params.substring(params.indexOf(" ") + 1).trim().toLowerCase()
			val countstr = params.substring(0, params.indexOf(" "))
			if (countstr.forall(Character.isDigit))  // Test to see if it's an integer
				count = countstr.toInt
			else {
				count = -1
				println(s"${BOLD}${RED}ERROR:${WHITE} Invalid parameter '$countstr': When using 'ADD count item' the 'count' must be an integer.${RESET}")
			}
		} else {
			item = params.toLowerCase()
			count = 1  // Default to adding 1 item
		}
		if (!itemlist.contains(item)) {
			count = -1
			println(s"'$item' is not a recognized item.")
		}
		if (count > 0) {
			playerChangeItem(item, count)
			if (count == 1)
				println(s"You got a $item.")
			else
				println(s"You got $count " + itemlist(itemlist.indexOf(item) + 1) + ".")
		}
	}

	/**
	  * BREAK: Breaks an item into its component parts, if possible.
	  *
	  * @param param - The item to break.
	  */
	def breakItem(param: String):Unit = {
		val item = param.toLowerCase()
		if (playerHasItem(item) > 0) {
			var resultSet = db.executeQuery(s"SELECT ComboID, ItemA, ItemB FROM combinations WHERE NewItem = '$item';")
			if (!resultSet.next())
				println("This item cannot be broken into separate items.")
			else {
				val item1 = resultSet.getString("ItemA")
				val item2 = resultSet.getString("ItemB")
				val comboID = resultSet.getString("ComboID")
				// *** vvv This should be done as a single transaction. vvv
				playerChangeItem(item, -1)
				playerChangeItem(item1, 1)
				playerChangeItem(item2, 1)
				// *** ^^^ This should be done as a single transaction. ^^^
				println(s"You got a $item1 and a $item2.")
				resultSet = db.executeQuery(s"SELECT * FROM knowncombos WHERE ComboID = '$comboID' AND PlayerName = '$playername';")
				if (!resultSet.next()) {  // Add new known item combination
					db.executeUpdate(s"INSERT INTO knowncombos (PlayerName, ComboID) VALUES ('$playername', '$comboID');")
					resultSet = db.executeQuery(s"SELECT COUNT(*) AS Count FROM knowncombos WHERE PlayerName = '$playername';")
					if (resultSet.next())
						println("You learned " + resultSet.getInt("Count") + s" of $itemcombinations possible item recipies!")
				}
			}
		} else
			println(s"You have no $item to break.")
	}

	/**
	  * CHEATS: Shows a list of "cheat" commands and what they do.
	  */
	def showCheats():Unit = {
		println(s"""
${BOLD}CHEATS${RESET}
	Lists these "cheat" commands.
${BOLD}ADD${RESET} item
	Adds that item to your inventory if it exists.
${BOLD}ADD${RESET} count item
	Adds "count" copies of that item to your inventory.
${BOLD}CLR, CLEAR${RESET}
	Clears the screen.
${BOLD}GO${RESET} coordinates
	Moves you to that room if one exists at those coordinates.
${BOLD}MAKERS${RESET}
	Gives you a wufflar, dampish, cistle, suvar, and nabob.
${BOLD}SMARTEN${RESET}
	You now know how to create all items.
${BOLD}WHERE${RESET}
	Tells you your current room coordinates.
  		""")
	}

	/**
	  * DROP: Moves items from the player's inventory into the room.
	  *
	  * @param params - The item to drop, possibly preceded by an optional quantity.
	  */
	def dropItem(params: String):Unit = {
		var item = ""
		var count = 1
		var itemCount = 0
		if (params.toLowerCase() == "all") {  // Drop all items into the room
			// ToDo: Handle the "all" case
		} else {
			if (params.indexOf(" ") > 0) {
				item = params.substring(params.indexOf(" ") + 1).trim().toLowerCase()
				itemCount = playerHasItem(item)
				val countstr = params.substring(0, params.indexOf(" "))
				if (countstr.forall(Character.isDigit))  // Test to see if it's an integer
					count = countstr.toInt
				else {  // "count" parameter isn't an integer
					itemCount = -1
					println(s"${BOLD}${RED}ERROR:${WHITE} Invalid parameter '$countstr': When using 'DROP count item' the 'count' parameter must be an integer.${RESET}")
				}
			} else {
				item = params.toLowerCase()
				itemCount = playerHasItem(item)
				count = 1  // Default to dropping 1 item
			}
			if (itemCount > 0) {
				// *** vvv This should be done as a single transaction. vvv
				playerChangeItem(item, -count)
				roomChangeItem(item, count)
				// *** ^^^ This should be done as a single transaction. ^^^
				if (count == 1)
					println(s"Dropped a " + item + ".")
				else
					println(s"Dropped $count " + itemlist(itemlist.indexOf(item) + 1) + ".")
			} else if (itemCount == 0) {
				println(s"You have no $item to drop.")
			}
		}
	}

	/**
	  * GET: Moves items from the room into the player's inventory.
	  *
	  * @param params - The item to pick up, possibly preceded by an optional quantity.
	  */
	def getItem(params: String):Unit = {
		var item = ""
		var count = 1
		var itemCount = 0
		if (params.toLowerCase() == "all") {  // Get all items in the room
			// ToDo: Handle the "all" case
		} else {
			if (params.indexOf(" ") > 0) {
				item = params.substring(params.indexOf(" ") + 1).trim().toLowerCase()
				itemCount = roomHasItem(item)
				val countstr = params.substring(0, params.indexOf(" "))
				if (countstr.forall(Character.isDigit))  // Test to see if it's an integer
					count = countstr.toInt
				else {
					itemCount = -1
					println(s"${BOLD}${RED}ERROR:${WHITE} Invalid parameter '$countstr': When using 'GET count item' the 'count' must be an integer.${RESET}")
				}
			} else {
				item = params.toLowerCase()
				itemCount = roomHasItem(item)
				count = itemCount  // Default to getting all items of that type
			}
			if (itemCount > 0) {
				// *** vvv This should be done as a single transaction. vvv
				roomChangeItem(item, -count)
				playerChangeItem(item, count)
				// *** ^^^ This should be done as a single transaction. ^^^
				if (count == 1)
					println(s"Got a " + item + ".")
				else
					println(s"Got $count " + itemlist(itemlist.indexOf(item) + 1) + ".")
			} else if (itemCount == 0) {
				println("You don't see a " + item + " here to pick up.")
			}
		}
	}

	/**
	  * HELP: Shows a helpful list of game commands and what they do.
	  */
	def showHelp():Unit = {
		println(s"""
${BOLD}HELP${RESET}
	Shows this help list.  The first letter of each command also works and capitalization doesn't matter.
${BOLD}LOOK${RESET}
	Looks at the current room.
${BOLD}LOOK${RESET} object
	Looks at the player or item if it's nearby.
${BOLD}LOOK SELF${RESET}
	Describes how you look.
${BOLD}INV${RESET}
	Displays a list of items in your inventory.
${BOLD}GET${RESET} item
	Adds all of the items of that type in the room into your inventory.
${BOLD}GET${RESET} count item
	Moves up to "count" of that item from the room into your inventory.
${BOLD}GET ALL${RESET}
	Puts all of the items in the room into your inventory.
${BOLD}DROP${RESET} item
	Removes one of that item from your inventory and drops it into the room.
${BOLD}DROP${RESET} count item
	Drops up to "count" of that item from your inventory into the room.
${BOLD}DROP${RESET} ALL
	Drops everything in your inventory into the room.
${BOLD}USE${RESET} item
	Uses the item if it's usable and there's one nearby.
${BOLD}MIX${RESET} item1 item2
	Attempts to combine two items.  Not all items can be combined.
${BOLD}KNOWN${RESET}
	Lists all item recipies you currently know.
${BOLD}KNOWN${RESET} item
	Lists item recipies you currently know which include that item.
${BOLD}BREAK${RESET} item
	Separates an item into its component parts if possible.
${BOLD}CREATE${RESET} item
	Creates the item if you know how to do it and have all of the needed parts.
${BOLD}CREATE${RESET} count item
	Creates "count" copies of the item if you can.
${BOLD}TRASH${RESET} item
	Destroys one of that item in your inventory.
${BOLD}TRASH${RESET} count item
	Destroys up to "count" copies of that item in your inventory.
${BOLD}NORTH, SOUTH, EAST, WEST${RESET}
	Goes that direction if possible.
${BOLD}QUIT${RESET}
	Saves and quits the game.
  		""")
	}

	/**
	  * INVENTORY: Shows the contents of the player's inventory
	  */
	def showInventory():Unit = {
		val resultSet = db.executeQuery(s"SELECT ItemName, Quantity FROM inventory WHERE PlayerName = '$playername';")
		if (resultSet.next()) {
			print(s"${BOLD}You're carrying:${RESET} ")
			var q = resultSet.getInt("Quantity")
			var item = resultSet.getString("ItemName")
			if (q == 1)
				print("a " + item)
			else if (item.substring(item.length - 1) == "s")
					print(q + " " + item + "es")
				else
					print(q + " " + item + "s")
			while (resultSet.next()) {
				q = resultSet.getInt("Quantity")
				item = resultSet.getString("ItemName")
				if (q == 1)
					print(", a " + item)
				else if (item.substring(item.length - 1) == "s")
						print(s", $q " + item + "es")
					else
						print(s", $q " + item + "s")
			}
			println("")
		} else
			println("You have nothing in your inventory.")
	}

	/**
	  * KNOWN: Lists known item recipies.
	  *
	  * @param param - An item name or an empty string for all known item recipies.
	  */
	def knownCombinations(param: String):Unit = {
		if (param == "") {  // List all known item recipies
			var resultSet = db.executeQuery(s"SELECT ComboID FROM knowncombos WHERE PlayerName = '$playername';")
			if (!resultSet.next())
				println("You don't know any item recipies yet.")
			else {
				// Get all known combo IDs.
				var combos = Array(resultSet.getString("ComboID"))
				while (resultSet.next())
					combos :+= resultSet.getString("ComboID")
				combos = combos.sorted
				println(s"${BOLD}Known item recipies: " + combos.length + s" of $itemcombinations${RESET}")
				for(id<-combos) {
					resultSet = db.executeQuery(s"SELECT * FROM combinations WHERE ComboID = '$id';")
					if (resultSet.next())
						println("\t" + resultSet.getString("NewItem") + " = " + resultSet.getString("ItemA") + " + " + resultSet.getString("ItemB"))
				}
			}
		} else {  // List known item recipies for this item
			if (itemlist.contains(param.toLowerCase())) {
				var resultSet = db.executeQuery(s"SELECT ComboID FROM knowncombos WHERE PlayerName = '$playername';")
				if (!resultSet.next())
					println("You don't know any recipies with that item yet.")
				else {
					// Get all known combo IDs.
					var combos = Array(resultSet.getString("ComboID"))
					val item = param.toLowerCase()
					var item1 = ""
					var item2 = ""
					var newitem = ""
					var count = 0
					while (resultSet.next())
						combos :+= resultSet.getString("ComboID")
					combos = combos.sorted
					for(id<-combos) {
						resultSet = db.executeQuery(s"SELECT * FROM combinations WHERE ComboID = '$id';")
						if (resultSet.next()) {
							item1 = resultSet.getString("ItemA")
							item2 = resultSet.getString("ItemB")
							newitem = resultSet.getString("NewItem")
							if ((item1 == item) || (item2 == item) || (newitem == item)) {
								count += 1
								if (count == 1)
									println(s"${BOLD}Known recipies including $item:${RESET}")
								println(s"\t$newitem = $item1 + $item2")
							}
						}
					}
					if (count == 0)
						println("You don't know any recipies with that item yet.")
				}
			} else
				println(s"'$param' is not a recognized item.")
		}
	}

	/**
	  * LOOK: Looks at a player or item if it's nearby.
	  *
	  * @param param - A string containing either an item name, player name, or "self".
	  */
	def lookAt(param: String):Unit = {
		if (param == "") {
			showLocation();
		} else {
			if (param.toLowerCase() == "self") {  // Show player's own description
				val resultSet = db.executeQuery(s"SELECT Description FROM players WHERE PlayerName = '$playername';")
				if (resultSet.next()) {
					println(s"You are $playername.  You look like " + resultSet.getString("Description"))
				}
			} else if (itemlist.contains(param.toLowerCase())) {  // Show item description if it's nearby
				if ((playerHasItem(param) > 0) || (roomHasItem(param) > 0)) {
					val resultSet = db.executeQuery(s"SELECT Description FROM items WHERE ItemName = '$param';")
					if (resultSet.next()) {
						println(resultSet.getString("Description"))
					}
				} else {
					println(s"You don't see a $param here.")
				}
			} else {  // Attempt to show player's description
				val resultSet = db.executeQuery(s"SELECT Description FROM players WHERE PlayerName = '$param' AND Coordinates = $playerlocation;")
				if (resultSet.next()) {
					println(s"$param looks like " + resultSet.getString("Description"))
				} else {
					println(s"You don't see anything called '$param' here.")
				}
			}
		}
	}

	/**
	  * MAKERS: Gives the player a wufflar, dampish, cistle, suvar, and nabob.
	  */
	def getMakers():Unit = {
		playerChangeItem("wufflar", 1)
		playerChangeItem("dampish", 1)
		playerChangeItem("cistle", 1)
		playerChangeItem("suvar", 1)
		playerChangeItem("nabob", 1)
		println("Added item makers to your inventory.")
	}

	/**
	  * MIX: Attempts to attach two items to create a new item.
	  *
	  * @param params - The two items to attempt to mix.
	  */
	def mixItems(params: String):Unit = {
		if (params.indexOf(" ") > 0) {
			val item1 = params.substring(0, params.indexOf(" ")).toLowerCase()
			var item1count = playerHasItem(item1)
			val item2 = params.substring(params.indexOf(" ") + 1).trim().toLowerCase()
			var item2count = playerHasItem(item2)
			if (item1count == 0)
				println(s"You don't have a $item1 in your inventory.")
			else if (item2count == 0)
				println(s"You don't have a $item2 in your inventory.")
			else if (item1 == item2)  // Two items of the same type never mix
				println("You don't see any way to attach those two items together.")
			else {
				var resultSet = db.executeQuery(s"SELECT ComboID, NewItem FROM combinations WHERE (ItemA = '$item1' AND ItemB = '$item2') OR (ItemA = '$item2' AND ItemB = '$item1');")
				if (!resultSet.next())
					println("You don't see any way to attach those two items together.")
				else {
					val newitem = resultSet.getString("NewItem")
					val comboID = resultSet.getString("ComboID")
					// *** vvv This should be done as a single transaction. vvv
					playerChangeItem(item1, -1)
					playerChangeItem(item2, -1)
					playerChangeItem(newitem, 1)
					// *** ^^^ This should be done as a single transaction. ^^^
					println(s"You created a $newitem.")
					resultSet = db.executeQuery(s"SELECT * FROM knowncombos WHERE ComboID = '$comboID' AND PlayerName = '$playername';")
					if (!resultSet.next()) {  // Add new known item combination
						db.executeUpdate(s"INSERT INTO knowncombos (PlayerName, ComboID) VALUES ('$playername', '$comboID');")
						resultSet = db.executeQuery(s"SELECT COUNT(*) AS Count FROM knowncombos WHERE PlayerName = '$playername';")
						if (resultSet.next())
							println("You learned " + resultSet.getInt("Count") + s" of $itemcombinations possible item recipies!")
					}
				}
			}
		} else {
			println("You need to provide two items to mix.")
		}
	}

	/**
	  * TRASH: Destroys items in the player's inventory.
	  *
	  * @param params - The item to destroy, possibly preceded by an optional quantity.
	  */
	def trashItem(params: String):Unit = {
		var item = ""
		var count = 1
		var itemCount = 0
		if (params.indexOf(" ") > 0) {
			item = params.substring(params.indexOf(" ") + 1).trim().toLowerCase()
			itemCount = playerHasItem(item)
			val countstr = params.substring(0, params.indexOf(" "))
			if (countstr.forall(Character.isDigit))  // Test to see if it's an integer
				count = countstr.toInt
			else {  // "count" parameter isn't an integer
				itemCount = -1
				println(s"${BOLD}${RED}ERROR:${WHITE} Invalid parameter '$countstr': When using 'TRASH count item' the 'count' parameter must be an integer.${RESET}")
			}
		} else {
			item = params.toLowerCase()
			itemCount = playerHasItem(item)
			count = 1  // Default to destroying 1 item
		}
		if (itemCount > 0) {
			playerChangeItem(item, -count)
			if (count == 1)
				println(s"Destroyed a " + item + ".")
			else
				println(s"Destroyed $count " + itemlist(itemlist.indexOf(item) + 1) + ".")
		} else if (itemCount == 0) {
			println(s"You don't have a $item to destroy.")
		}
	}

	/**
	  * NORTH, SOUTH, EAST, WEST: Attempts to move the player in the given direction.
	  *
	  * @param direction - The direction to move.  Should only be "north", "south", "east", or "west".
	  */
	def travel(direction: String):Unit = {
		var resultSet = db.executeQuery(s"SELECT ToRoom FROM roomconnections WHERE FromRoom = $playerlocation AND DirName = '$direction';")
		if (resultSet.next()) {
			playerlocation = resultSet.getInt("ToRoom")
			resultSet = db.executeQuery(s"SELECT * FROM roomitems WHERE Coordinates = $playerlocation;")
			if (resultSet.next() == false) {
				// Add a random level 1 item to the room since it's empty
				val item = Seq("gwingus", "mongoo", "snuge", "torkus", "wigglesnort")(random.nextInt(5))
				db.executeUpdate(s"INSERT INTO roomitems (Coordinates, ItemName, Quantity) VALUES ($playerlocation, '$item', 1);")
			}
			// Update player location
			db.executeUpdate(s"UPDATE players SET Coordinates = $playerlocation WHERE PlayerName = '$playername';")
			showLocation()
		} else {
			println(s"You cannot go $direction from here.")
		}
	}

	/**
	  * USE: Attempts to use an item if one is nearby.
	  *
	  * @param item - A string containing an item name.
	  */
	def useItem(item: String):Unit = {
		if ((playerHasItem(item) > 0) || (roomHasItem(item) > 0)) {
			val resultSet = db.executeQuery(s"SELECT ItemUse FROM items WHERE ItemName = '$item' AND ItemUse IS NOT NULL;")
			if (resultSet.next()) {
				val use = resultSet.getString("ItemUse")
				val command = use.substring(0, use.indexOf(" "))
				val arg = use.substring(use.indexOf(" ") + 1).trim()
				command match {
					case "SAY"		=> println(arg)
					case "CREATE"	=> addItem(arg)
					case _			=> println(s"${BOLD}${RED}ERROR:${WHITE} Unknown command '$command' on item '$item'.${RESET}")
				}
			} else {
				println(s"You don't see a way to use the '$item'.")
			}
		} else {
			println(s"You don't see anything called '$item' here.")
		}
	}

	/**
	  * WHERE: Lets the player know their current coordinates.
	  */
	def whereAmI():Unit = {
		var resultSet = db.executeQuery(s"SELECT RoomName FROM rooms WHERE Coordinates = $playerlocation;")
		var roomname = ""
		if (resultSet.next()) {
			roomname = resultSet.getString("RoomName")
		}
		println(s"${BOLD}Present coordinates:${RESET} $playerlocation ($roomname)")
	}

	// Main function:

	/**
	  * Main FAZUUL initialization and game loop.
	  *
	  * @param args - Game parameters.  (Unused)
	  */
	def main(args: Array[String]):Unit = {
		println(s"$CLEARSCR\nPlease wait while FAZUUL builds its data...")

		// Connect to the MySQL database on the localhost
		val driver = "com.mysql.cj.jdbc.Driver"
		val url = "jdbc:mysql://localhost:3306"
		val username = "root"
		val password = "SQLAdmin1!"  // ToDo: Change this to your MySQL root password.
		var connection:Connection = null
		try {
			// Make the MySQL connection
			Class.forName(driver)
			connection = DriverManager.getConnection(url, username, password)
			db = connection.createStatement()

			// Create the "FAZUULData" database if needed
			db.executeUpdate("CREATE DATABASE IF NOT EXISTS FAZUULData;")
			db.executeUpdate("USE FAZUULData;")

			// Read the JSON file with the default setup data
			val filePath = System.getProperty("user.dir") + "/FazuulData.json"  // ToDo: For Windows, change this to "\\FazuulData.json" instead.
			val jsonString = fromFile(filePath).mkString
			val data = ujson.read(jsonString)

			// Read the item data from the JSON file into the "items" table
			db.executeUpdate("DROP TABLE IF EXISTS items")
			db.executeUpdate("CREATE TABLE items (ItemName VARCHAR(15), Plural VARCHAR(15), Description VARCHAR(2000), ItemUse VARCHAR(2000), PRIMARY KEY (ItemName));")
			var plural = ""
			var desc = ""
			var use = ""
			var cmd = ""
			var dat = data("items")
			for (itemname<-dat.obj.keys) {
				itemlist :+= itemname  // Build array of items and plurals
				plural = dat(itemname)("Plural").str
				itemlist :+= plural  // Build array of items and plurals
				desc = dat(itemname)("Description").str
				if (dat(itemname).obj.get("Use") != None) {
					use = dat(itemname)("Use").str
					cmd = "INSERT INTO items (ItemName, Plural, Description, ItemUse) VALUES ('" + itemname + "', '" + plural + "', '" + desc + "', '" + use + "');"
				} else
					cmd = "INSERT INTO items (ItemName, Plural, Description) VALUES ('" + itemname + "', '" + plural + "', '" + desc + "');"
				db.executeUpdate(cmd)
			}

			// Read the item data from the JSON file into the "combinations" table
			db.executeUpdate("DROP TABLE IF EXISTS combinations;")
			db.executeUpdate("CREATE TABLE combinations (ComboID VARCHAR(15), ItemA VARCHAR(15) NOT NULL REFERENCES items(ItemName), ItemB VARCHAR(15) NOT NULL REFERENCES items(ItemName), NewItem VARCHAR(15) NOT NULL REFERENCES items(ItemName), PRIMARY KEY (ComboID));")
			dat = data("combinations")
			for (id<-dat.obj.keys) {
				itemcombinations += 1
				cmd = "INSERT INTO combinations (ComboID, ItemA, ItemB, NewItem) VALUES ('" + id + "', '" + dat(id)("ItemA").str + "', '" + dat(id)("ItemB").str + "', '" + dat(id)("NewItem").str + "');"
				db.executeUpdate(cmd)
			}

			// Read the item data from the JSON file into the "rooms" table
			db.executeUpdate("DROP TABLE IF EXISTS rooms;")
			db.executeUpdate("CREATE TABLE rooms (Coordinates INT, RoomName VARCHAR(255), Description VARCHAR(2000), PRIMARY KEY (Coordinates));")
			dat = data("rooms")
			for (id<-dat.obj.keys) {
				cmd = "INSERT INTO rooms (Coordinates, RoomName, Description) VALUES (" + id + ", '" + dat(id)("Name").str + "', '" + dat(id)("Description").str + "');"
				db.executeUpdate(cmd)
			}

			// Read the item data from the JSON file into the "roomconnections" table
			db.executeUpdate("DROP TABLE IF EXISTS roomconnections;")
			db.executeUpdate("CREATE TABLE roomconnections (ID INT AUTO_INCREMENT, FromRoom INT NOT NULL REFERENCES rooms(Coordinates), ToRoom INT NOT NULL REFERENCES rooms(Coordinates), DirName VARCHAR(15) NOT NULL, CHECK (DirName in ('north','south','east','west')), PRIMARY KEY (ID));")
			dat = data("connections")
			for (id<-dat.obj.keys) {
				cmd = "INSERT INTO roomconnections (FromRoom, ToRoom, DirName) VALUES (" + dat(id)("From").str + ", " + dat(id)("To").str + ", '" + dat(id)("Direction").str + "');"
				db.executeUpdate(cmd)
			}

			// Create the "players" table if needed
			db.executeUpdate("CREATE TABLE IF NOT EXISTS players (PlayerName VARCHAR(15), Description VARCHAR(255) DEFAULT 'a normal human being.', Coordinates INT DEFAULT 1 REFERENCES rooms(Coordinates), PRIMARY KEY (PlayerName));")

			// Add a test player if one doesn't exist already.  // ToDo: You can comment out this section if you don't need a test player.
			var resultSet = db.executeQuery("SELECT * FROM players WHERE PlayerName = 'TestPlayer';")
			if (resultSet.next() == false) {  // Create "TestPlayer" since there isn't one
				db.executeUpdate("INSERT INTO players (PlayerName, Description, Coordinates) VALUES ('TestPlayer', 'a test player.', 294);")
			}

			// Create the "roomitems" table if needed
			db.executeUpdate("CREATE TABLE IF NOT EXISTS roomitems (ID INT AUTO_INCREMENT, Coordinates INT NOT NULL REFERENCES rooms(Coordinates), ItemName VARCHAR(15) NOT NULL REFERENCES items(ItemName), Quantity	INT NOT NULL, PRIMARY KEY (ID));")
			dat = data("roomitems")
			var q = 0;
			var r = 0
			for (id<-dat.obj.keys) {
				q = dat(id)("Quantity").str.toInt
				r = dat(id)("Room").str.toInt
				resultSet = db.executeQuery(s"SELECT Quantity FROM roomitems WHERE Coordinates = $r;")
				if (resultSet.next() == false) {  // Add item to room
					cmd = "INSERT INTO roomitems (Coordinates, ItemName, Quantity) VALUES (" + dat(id)("Room").str + ", '" + dat(id)("Item").str + "', " + q + ");"
					db.executeUpdate(cmd)
				} else if (resultSet.getInt("Quantity") < q) {  // Increase the quantity of the item in the room up to the amount read in
					cmd = "UPDATE roomitems SET Quantity = " + q + " WHERE Coordinates = " + dat(id)("Room").str + " AND ItemName = '" + dat(id)("Item").str + ";"
					db.executeUpdate(cmd)
				}
			}

			// Create the "inventory" table if needed
			db.executeUpdate("CREATE TABLE IF NOT EXISTS inventory (ID INT AUTO_INCREMENT, PlayerName VARCHAR(15) NOT NULL REFERENCES players(PlayerName), ItemName VARCHAR(15) NOT NULL REFERENCES items(ItemName), Quantity INT DEFAULT 1, PRIMARY KEY (ID));")

			// Create the "knowncombos" table if needed
			db.executeUpdate("CREATE TABLE IF NOT EXISTS knowncombos (ID INT AUTO_INCREMENT, PlayerName VARCHAR(15) NOT NULL REFERENCES players(PlayerName), ComboID VARCHAR(15) NOT NULL REFERENCES items(ComboID), PRIMARY KEY (ID));")

			// Create the "seenrooms" table if needed
			db.executeUpdate("CREATE TABLE IF NOT EXISTS seenrooms (ID INT AUTO_INCREMENT, PlayerName VARCHAR(15) NOT NULL REFERENCES players(PlayerName), Coordinates INT NOT NULL REFERENCES rooms(Coordinates), PRIMARY KEY (ID));")

			// *** Make sure that all players are in existent rooms?

			println(s"""$BACKLN${BOLD}
			FFFF   A    ZZZZ U   U U   U L
			F     A A     Z  U   U U   U L
			FFF  AAAAA   Z   U   U U   U L
			F   A     A ZZZZ  UUU   UUU  LLLL
			${RESET}""")
			println("Based on the game by Tim Stryker\n")
			print(s"${BOLD}Enter your name:${RESET} ")
			var err = false
			while (playername == "") {  // Make sure that the player name is valid and not an item name
				playername = validate(readLine(), 1)
				if (playername == "") {
					if (err)
						print(s"$BACKLN")
					print(s"$BACKLN${BOLD}${RED}ERROR:${WHITE} Player name cannot be blank and must only consist of alphanumeric characters.\nEnter your name:${RESET} ")
					err = true
				}
				if (itemlist.contains(playername.toLowerCase())) {
					if (err)
						print(s"$BACKLN")
					print(s"$BACKLN${BOLD}${RED}ERROR:${WHITE} '$playername' is a reserved name, please choose another name.\nEnter your name:${RESET} ")
					err = true
					playername = ""
				}
			}
			if (err)
				print(s"$BACKLN")
			println(s"$BACKLN${BOLD}Your name:${RESET} $playername")

			// Create player if they don't already exist
			resultSet = db.executeQuery(s"SELECT Coordinates FROM players WHERE PlayerName = '$playername';")
			if (resultSet.next()) {  // Get stored player location
				playerlocation = resultSet.getInt("Coordinates")
			} else {  // Player is new, so add them to the database
				db.executeUpdate(s"INSERT INTO players (PlayerName, Description, Coordinates) VALUES ('$playername', 'a normal human being.', 1);")
				println(s"\nWelcome to FAZUUL, $playername!\n\nYou can type '${BOLD}help${RESET}' or just '${BOLD}h${RESET}' for a list of commands.")
			}

			// Display initial location information
			showLocation(true)

			// Main game loop.
			var input = ""
			var command = ""
			var params = ""
			var continue = true
			while (continue) {
				// Command prompt
				print("> ")
				input = readLine().trim()
				if (input.indexOf(" ") > 0) {
					command = input.substring(0, input.indexOf(" ")).toLowerCase()
					params = input.substring(input.indexOf(" ") + 1).trim()
				} else {
					command = input.toLowerCase()
					params = ""
				}
				if (command != "")  // Execute command
					command match {  // ToDo: You may want to disable some or all cheat commands here.
						case "add"							=> addItem(validate(params, 2))  // Cheat command
						case "b" | "break"					=> breakItem(validate(params, 1))
						// case "c" | "create"				=> createItem(validate(params, 2))
						case "cheats"						=> showCheats()  // Cheat command
						case "clr" | "cls" | "clear"		=> println(s"$CLEARSCR")  // Cheat command
						case "d" | "drop"					=> dropItem(validate(params, 2))
						case "e" | "east"					=> travel("east")
						case "g" | "get"					=> getItem(validate(params, 2))
						// case "go"						=> go(validate(params, 1))  // Cheat command
						case "h" | "help"					=> showHelp()
						case "i" | "inv" | "inventory"		=> showInventory()
						case "k" | "known"					=> knownCombinations(validate(params, 1))
						case "l" | "look" if (params == "")	=> showLocation(true)
						case "l" | "look" if (params != "")	=> lookAt(validate(params, 1))
						case "m" | "mix"					=> mixItems(validate(params, 2))
						case "makers"						=> getMakers()  // Cheat command
						case "n" | "north"					=> travel("north")
						case "q" | "quit" | "exit"			=> continue = false
						case "s" | "south"					=> travel("south")
						// case "smarten"					=> smarten()  // Cheat command
						case "t" | "trash"					=> trashItem(validate(params, 2))
						case "u" | "use"					=> useItem(validate(params, 1))
						case "w" | "west"					=> travel("west")
						case "where"						=> whereAmI()  // Cheat command
						case _								=> println(s"${BOLD}${RED}ERROR:${WHITE} Unknown command '$command'${RESET}\nTry using 'help' to get a list of commands.\n")
					}
			}

			// Done
			println("\nThank you for playing!\n")
		} catch {
			case e: Throwable => e.printStackTrace
		}
		connection.close()
	}
}