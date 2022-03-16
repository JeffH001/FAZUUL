name := "FAZUUL"
ThisBuild / organization     := "com.JeffH001"
ThisBuild / organizationName := "JeffH001"

ThisBuild / scalaVersion     := "2.12.15"
ThisBuild / version          := "0.1"

libraryDependencies += "org.apache.spark" %% "spark-core" % "3.1.2"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.1.2"
libraryDependencies += "org.apache.spark" %% "spark-hive" % "3.1.2"
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.28"
libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5"