package com.nerdfm.game

import kotlin.random.Random

class NationDef(
    val name: String,
    val weight: Int,
    val first: List<String>,
    val pre: List<String>,
    val mid: List<String>,
    val suf: List<String>
)

private fun l(s: String): List<String> = s.split(" ")

object Names {
    val nations: List<NationDef> = listOf(
        NationDef("Valdoria", 30,
            l("Luca Marco Dario Enzo Nico Matteo Ruben Tomas Emil Vito Aldo Remo Fabio Gino Livio Orso Sandro Teo"),
            l("Ros Ber Cas Mon Val Gal Fer Lun Tor Mar Ser Bel Cor Pal"),
            l("a e o i an en"),
            l("ini etti ano ero ari elli oni ati ese ello")),
        NationDef("Nordheim", 8,
            l("Anders Bjorn Erik Leif Soren Magnus Olaf Tor Henrik Kasper Mikkel Sven Axel Ivar Nils Ragnar"),
            l("Berg Lund Strand Hald Vik Dal Fjord Eng Holm Ask Sund Rud"),
            l("a e en er"),
            l("sen gren qvist mark by nes dahl vold son rup")),
        NationDef("Kessara", 8,
            l("Dragan Milos Pavel Tibor Zoran Bogdan Radek Viktor Lazar Marek Goran Stanko Ivo Damir Zlatko Branko"),
            l("Kov Nov Petr Vuk Jan Stoj Brz Mil Drag Zel Hor Tur"),
            l("a o e ic"),
            l("ic ov ski ak enko ec ar ek an ovic")),
        NationDef("Almeron", 10,
            l("Joao Diego Mateo Pedro Raul Hugo Andres Iker Pablo Alvaro Rafa Sergio Nuno Tiago Xavi Bruno Lucio"),
            l("Mor Sant Gar Her Rib Vil Ort Cal Mend Cab Alv Nav"),
            l("a e al en o"),
            l("ez ales ino eda ero eiro ado ana oza ante")),
        NationDef("Tamburi", 9,
            l("Kofi Ade Kwame Tunde Chike Sekou Moussa Femi Yaw Idris Amadou Kelechi Bakary Emeka Seun Musa Obi"),
            l("Ba Dia Ko Man Ndu Ola Sow Tra Ade Oko Kan Mba"),
            l("ma ko ra la ne"),
            l("ye ra ba ne gu lo ki di ta nde")),
        NationDef("Zephyria", 6,
            l("Nikos Stavros Kostas Lazaros Petros Yannis Thanos Dimitri Aris Makis Orestis Leonidas Vasili Sotiris Manos"),
            l("Papa Kara Theo Sakel Mav Pana Lamb Xen Kon Stav Dimi Vlach"),
            l("o a i ela"),
            l("poulos idis akis ellis opoulos atos akos onis idou eas")),
        NationDef("Brannock", 12,
            l("Harry Jack Oliver Callum Reece Connor Dean Liam Declan Archie Rory Toby Elliot Finn Jamie Mason Kyle"),
            l("Ash Brad Clay Fox Hay Kirk Marsh Pen Rad Stan Wal Whit Pear Thorn"),
            l("e ing er ley"),
            l("ley ton ford wood worth by well shaw man field croft hurst")),
        NationDef("Quillon", 9,
            l("Louis Hugo Remi Theo Axel Julien Bastien Maxime Corentin Lucien Yann Gaspard Pierre Quentin Etienne Mael"),
            l("Dub Lef Mar Fou Dur Gir Lam Roc Bel Cha Mou Vin"),
            l("e a o ai"),
            l("ois ier ault eau ard ont ette ain our eux ac")),
        NationDef("Daruvia", 6,
            l("Emre Kerem Burak Selim Tarik Cem Yusuf Omar Karim Nabil Hakan Mert Rami Faruk Zaid Anil"),
            l("Yil Kar Dem Aksu Tur Ozd Kay Bal Sar Nas Hal Mah"),
            l("a e al ur"),
            l("maz oglu lu er kaya han bey ali oun ani")),
        NationDef("Ixtal", 9,
            l("Santi Lucho Joaquin Facu Mateo Thiago Nacho Leandro Bautista Cristo Ezequiel Tadeo Lautaro Gael Dante Ramiro"),
            l("Cor Mont Ruiz Pel Gonz Her Fig Sal Pad Arr Ber Vega"),
            l("a e o and in"),
            l("dos ez ero ola ana ito oza illa edo ales")),
        NationDef("Korvane", 8,
            l("Lukas Jonas Felix Matthias Tobias Kilian Jannik Florian Lennart Dieter Ralf Bastian Moritz Anton Ewald Stefan"),
            l("Brand Kess Hoff Schu Weber Krau Bau Vogel Zim Hart Rein Rau"),
            l("er en el s"),
            l("mann ner ler berg stein feld haus meyer bach hardt burg")),
        NationDef("Itsuki", 5,
            l("Haruto Ren Sora Kaito Yuto Riku Daiki Takumi Kenji Shun Hiroshi Akira Daichi Noboru Taro Ryota"),
            l("Tana Yama Naka Kobay Fuji Mori Hayas Sato Take Isha Mura Ogu"),
            l("ka sa mi ta"),
            l("da moto shi wa mura gawa saki hara oka no zawa"))
    )

    private val totalW = nations.sumOf { it.weight }

    fun pickNation(rnd: Random): Int {
        var r = rnd.nextInt(totalW)
        for (i in nations.indices) {
            r -= nations[i].weight
            if (r < 0) return i
        }
        return 0
    }

    fun fullName(nat: Int, rnd: Random): String {
        val n = nations[nat]
        val f = n.first[rnd.nextInt(n.first.size)]
        val mid = if (rnd.nextInt(3) == 0) n.mid[rnd.nextInt(n.mid.size)] else ""
        var s = n.pre[rnd.nextInt(n.pre.size)] + mid + n.suf[rnd.nextInt(n.suf.size)]
        s = s.replaceFirstChar { it.uppercase() }
        return "$f $s"
    }

    val clubPre = l("Ash Black Brook Cold Dun East Fair Green Grey High Iron Kings Lake Long Mill Moss New North Oak Old Port Red Rock Salt Silver South Stone Storm Swan Thorn West White Wolf Raven Amber Cinder Hollow Marsh Frost Briar Copper")
    val clubSuf = l("ford ton bury field wick mouth haven gate bridge moor dale port stead wood cliff vale ham worth ley bourne")
    val clubWord = l("FC United Athletic Rovers Town City Albion Wanderers Rangers County Borough Victoria Argyle")
}
