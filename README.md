# FAZUUL
## Table of Contents

* <a href="#project-description">Project Description</a>
* <a href="#installation">Installation</a>
* <a href="#made-with">Made With...</a>
* <a href="#about-fazuul">About "FAZUUL"</a>
* <a href="#main-map">Main Map</a>
* <a href="#database-entity-relationship">Database Entity Relationship</a>

## Project Description
This is an example project created using Scala and MySQL to read a JSON file, build a database from it, and then provide a small playable game using a command line interface.

The game is a very minimal implementation of the game "FAZUUL" by Tim Stryker, originally published in 1984.  This game was among the first Multi User Dungeons (MUDs), where several people could play the same game together online at the same time.  For more information on this game <a href="https://en-academic.com/dic.nsf/enwiki/3185151">see here</a>.

## Installation

If you have Scala and MySQL installed, simply clone this repository, edit any parts of `src/main/scala/PlayFAZUUL.scala` which are marked with `// ToDo:` as needed (based on the text in the comments there), open SBT in the root directory, and then `run` it in SBT.  The code will automatically create the database and all tables needed using the `FazuulData.json` file.

## Made With...
- Scala v2.12.15
- Java v8 (v1.8.0_312)
- MySQL v8.0.28
- VSCode v1.65.2
- - Scala (Metals) extension (by Scalameta) v1.13.0
- - MySQL extension (by cweijan) v4.8.4
- - Remote Development extension (by Microsoft) v0.21.0
- Ubuntu v20.04 LTS

## About "FAZUUL"

<center><img alt="FAZUUL product image" src="/images/Fazuul.jpg?raw=true" height=300></center>

FAZUUL is a puzzle game set on an alien world with bizarre objects that you could combine and use to solve various puzzles.  In this implementation I've only added the first four levels of items, which you can combine in different ways to unlock 30 possible recipies for a total of 35 different items.  See if you can collect them all!

As you explore, any room you enter that would be empty will have a "level 1" item added to it.  You can `MIX` level 1 items to make level 2 items.  Level 2 items can then sometimes be `MIX`ed to make level 3 items, which themselves can be `MIX`ed to make level 4 items.  You can also `BREAK` a level 2 or higher item back into its component parts.  Additionally, some higher level items can be `USE`d to produce as many level 1 items as you need.

You can enter `HELP` or `H` at any time after you've logged in to see a list of commands (which can be used in upper- or lower-case, and can be abbreviated to just their first letter).

## Main Map
If you're having trouble navigating the world of FAZUUL, here's the map that was used.

<center><img alt="FAZUUL product image" src="/images/city_floor1.gif?raw=true" height=500></center>

(Image courtesy of "Sean's Guide to FAZUUL")

## Database Entity Relationship

<center><img alt="FAZUUL product image" src="/images/FAZUUL_ER_Diagram.png?raw=true" height=500></center>

The database is set up to track players, items, rooms, and the intersections between those things.  The tables used in the game represent the following data:

* `players` - Players and player info.
* `inventory` - Player inventories.
* `items`* - Level 1-4 Fazuul items.
* `combinations`* - Possible combinations of items using the "MIX" command and what item they produce.
* `knowncombos` - Tracks what combinations each player has discovered.
* `rooms`* - Room names and their descriptions.
* `roomconnections`* - Connections between rooms.
* `seenrooms` - Tracks what rooms each player has seen.
* `roomitems`* - Items available in the rooms.

Tables marked with an "*" above are ones which are partially or completely populated from the included `FazuulData.json` file.

Enjoy!