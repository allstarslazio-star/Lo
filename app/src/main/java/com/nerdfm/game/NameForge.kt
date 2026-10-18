package com.nerdfm.game

import kotlin.random.Random

enum class Gender { MALE, FEMALE }

/**
 * What the generator needs to know about the person being named.
 * [age] picks the generation of given names (older players get more classic names), [foreignChance] is the chance that
 * the family name comes from another nation (mixed heritage), [nicknames] allows a "known as" nickname.
 */
class NameOptions(
    val gender: Gender = Gender.MALE,
    val age: Int = 25,
    val foreignChance: Float = 0.10f,
    val nicknames: Boolean = true
)

/** [full] is what the rest of the game stores as the name. The other forms are for tight screens and flavour. */
class PersonName(
    val given: String,
    val surname: String,
    val nickname: String?,
    val gender: Gender,
    val nation: Int,
    /** Which naming pattern was used, e.g. "plain", "double", "particle", "patronymic", "prefix", "hyphen", "mixed". */
    val pattern: String
) {
    val full: String get() = "$given $surname"
    /** "L. Rossetti" */
    val short: String get() = given.first() + ". " + surname
    /** "Luca 'Rocket' Rossetti" when a nickname exists. */
    val withNickname: String get() = if (nickname == null) full else "$given '$nickname' $surname"
}

/**
 * A richer, opt-in name generator. The original [Names.fullName] is untouched (its random draws are part of the pinned
 * world-generation behaviour); a world uses this one only when [World.richNames] is set.
 *
 * Versatility: given names by generation and gender, curated family names plus cleaned-up syllable names, per-nation
 * naming customs (double surnames, particles such as "di" or "von", patronymics, Mc/O' prefixes, "al-", hyphenated names,
 * feminine surname endings), mixed heritage, nicknames, a blocklist and quality checks, and a duplicate fixer.
 */
object NameForge {
    private class Flavour(
        val classic: List<String>,
        val female: List<String>,
        val surnames: List<String>,
        val particles: List<String> = emptyList(), val particleChance: Float = 0f,
        val doubleChance: Float = 0f, val hyphenChance: Float = 0f,
        val patronymic: String? = null, val patronymicChance: Float = 0f,
        val prefixes: List<String> = emptyList(), val prefixChance: Float = 0f,
        val hyphenGiven: Float = 0f,
        val femaleEnd: List<Pair<String, String>> = emptyList()
    )

    private fun l(s: String): List<String> = s.split(" ")

    private val FLAVOUR: Map<String, Flavour> = mapOf(
        "Valdoria" to Flavour(
            l("Giuseppe Antonio Carlo Franco Mario Paolo Bruno Gianni Piero Vittorio Salvo Lorenzo Renato Aurelio Claudio Ettore Ignazio Silvio"),
            l("Giulia Chiara Sofia Elena Marta Lucia Federica Alessia Bianca Serena Ilaria Camilla Valentina Francesca Beatrice Silvia Noemi Paola Greta Elisa"),
            l("Rossetti Bianchini Moretti Carbone Ferraro Bellini Gallo Conti Lombardi Marchetti Costa Fontana Santoro Barbieri Pellegrini Monti Ricci Sartori Vitale Orlandi Greco Caruso Neri Basile"),
            particles = l("di de"), particleChance = 0.04f),
        "Nordheim" to Flavour(
            l("Gunnar Harald Knut Arne Bjarne Torstein Halvor Einar Trygve Rolf Sigurd Odd Hakon Thor Gustav Birger Stig Vidar"),
            l("Ingrid Astrid Freya Sigrid Hanna Linnea Maja Sofie Karin Thea Solveig Elin Marit Ida Liv Signe Tuva Helga Runa Frida"),
            l("Lindqvist Halvorsen Bergman Nygaard Solberg Eklund Haugen Dahlgren Skoglund Overgaard Brekke Falk Rasmussen Winther Aasen Lundgren Moen Kvam Sandvik Roald Thorsen Vang Isaksen Holmgren"),
            patronymic = "sen", patronymicChance = 0.35f),
        "Kessara" to Flavour(
            l("Josip Zdenko Vladimir Ratko Dusan Ivica Slavko Bruno Janko Miroslav Radovan Vlado Anton Karlo Stipe Nenad Franjo Vojtech"),
            l("Ana Marija Petra Jelena Katarina Tereza Milena Ivana Lucija Nina Dara Vesna Mira Tanja Eva Irena Zora Dragica Branka Sanja"),
            l("Kovacevic Novakov Petrovic Vukovic Jankowski Stojanov Brezovec Milanov Draganic Zelenka Horvat Turkovic Babic Markovic Pavlic Lazarev Boskovic Tomasek Rakic Simunic Filipov Matic Gorski Blazek"),
            hyphenChance = 0.03f,
            femaleEnd = listOf("ov" to "ova", "ev" to "eva", "ski" to "ska")),
        "Almeron" to Flavour(
            l("Manuel Francisco Jose Antonio Luis Ramon Enrique Fernando Joaquin Ignacio Alfonso Rodrigo Emilio Ricardo Jesus Tomas Cesar Gonzalo"),
            l("Maria Carmen Lucia Isabel Pilar Rosa Teresa Elena Marta Paula Alba Irene Sara Laia Nuria Ines Beatriz Lola Marina Olalla"),
            l("Garrido Navarro Ferrer Molina Castillo Prieto Serrano Delgado Montero Aguirre Vidal Campos Lorente Pascual Roldan Benitez Salinas Quiroga Mora Duarte Cordero Ibarra Fuentes Marin"),
            particles = l("de"), particleChance = 0.06f, doubleChance = 0.40f),
        "Tamburi" to Flavour(
            l("Kwesi Baba Yakubu Jabari Ousmane Cheikh Mamadou Ibrahima Abdou Lamine Samba Djibril Gora Nana Kwabena Issa Tidiane Boubacar"),
            l("Amina Ama Ngozi Fatou Abena Adaeze Zainab Khadija Efua Nia Aisha Yetunde Chiamaka Mariam Sade Akosua Awa Binta Thandi Kemi"),
            l("Diallo Mensah Okafor Traore Boateng Adeyemi Ndiaye Konate Osei Balogun Sarr Coulibaly Okonkwo Amoah Camara Eze Bangura Tetteh Lawal Keita Ouedraogo Nwosu Sesay Danso")),
        "Zephyria" to Flavour(
            l("Giorgos Christos Alexandros Evangelos Panos Spyros Haris Vangelis Fotis Telis Andreas Kyriakos Pavlos Stelios Argyris Michalis Theodoros Nektarios"),
            l("Eleni Maria Sofia Katerina Dimitra Anastasia Ioanna Despina Irini Popi Vasiliki Zoe Eirini Fotini Marina Georgia Niki Roula Ariadne Lia"),
            l("Georgiou Karras Vasilakis Mantzios Louloudis Kostopoulos Petrakis Anagnos Sarris Dalakis Mitsos Triantis Galanis Pappas Rallis Zografos Kyriazis Fotiadis Loukas Stamatis Vlachos Antoniou Manolis Dendias"),
            femaleEnd = listOf("poulos" to "poulou", "idis" to "idou", "akis" to "aki")),
        "Brannock" to Flavour(
            l("Albert Arthur Frank Stanley Norman Cyril Reginald Walter Percy Ernest Leonard Clifford Harold Roy Bernard Desmond Wilfred Gordon"),
            l("Emily Charlotte Amelia Hannah Beatrice Lucy Grace Eleanor Florence Imogen Harriet Edith Poppy Ruby Evelyn Matilda Joan Violet Alice Margaret"),
            l("Pearson Hargreaves Whitlock Ashworth Fairbank Kirkby Thornley Radcliffe Hollis Garside Bamford Tattersall Lockwood Ormerod Pickering Sutcliffe Wainwright Dunmore Calloway Hartley Blythe Moorcroft Rowntree Sedgwick"),
            hyphenChance = 0.05f, prefixes = l("Mc O' Mac"), prefixChance = 0.07f),
        "Quillon" to Flavour(
            l("Jean Pierre Michel Andre Rene Marcel Gerard Henri Claude Alain Bernard Jacques Fernand Raymond Lucien Philippe Roger Gaston"),
            l("Camille Margaux Juliette Manon Chloe Elodie Amelie Lea Capucine Oceane Colette Margot Sidonie Clemence Solene Anouk Mathilde Odile Brigitte Delphine"),
            l("Beaumont Laurent Moreau Fontaine Marchand Delacroix Rousseau Girard Colbert Lambert Perrin Vasseur Chevalier Brunet Leclerc Dupuis Maillard Gauthier Lacombe Renard Navarre Bouchard Tessier Cormier"),
            particles = l("de Le Du La"), particleChance = 0.10f, hyphenGiven = 0.12f),
        "Daruvia" to Flavour(
            l("Mustafa Ahmet Mehmet Hasan Ibrahim Osman Halil Kemal Ismail Tahir Samir Walid Jamal Rashid Fadi Nadim Salim Anwar"),
            l("Leyla Zeynep Amira Selin Yasmin Nour Dalia Rania Samira Laila Hana Aylin Meryem Esra Farah Lina Sabah Zehra Dina Ayla"),
            l("Yildirim Haddad Karaman Nasser Demir Khalil Sahin Aziz Korkmaz Mansour Aydin Farouk Ozturk Qasim Celik Habib Tanrikulu Saleh Bakr Erdem Idris Hamdan Yavuz Rahal"),
            prefixes = l("al- el-"), prefixChance = 0.08f),
        "Ixtal" to Flavour(
            l("Ricardo Alberto Jorge Hector Guillermo Osvaldo Mauricio Ernesto Claudio Rolando Nestor Gustavo Anibal Ramon Alejandro Fabian Horacio Cesar"),
            l("Valentina Camila Lucia Mariana Rocio Ximena Paloma Florencia Agustina Milagros Abril Catalina Julieta Renata Candela Belen Daniela Soledad Guadalupe Pilar"),
            l("Quintana Herrera Barrios Villalba Escobar Maldonado Acosta Cabrera Duarte Paredes Zapata Ledesma Rojas Medina Ibanez Sosa Benavidez Olmos Palacios Cardozo Ferreyra Lucero Montes Gimenez"),
            doubleChance = 0.35f),
        "Korvane" to Flavour(
            l("Hans Werner Klaus Jurgen Gerhard Dieter Helmut Rainer Wolfgang Heinz Karl Uwe Horst Manfred Otto Ludwig Siegfried Bernd"),
            l("Anna Greta Ingeborg Hannelore Katrin Lena Sabine Brigitte Heidi Marlene Ute Franziska Johanna Elke Renate Silke Mathilde Annika Lotte Gisela"),
            l("Hartmann Eberhardt Falkner Reinhold Steiner Albrecht Bergmann Kruger Lindner Vollmer Wendt Zeller Hofmann Brandt Sommer Kessler Neumann Roth Fischbach Ostermann"),
            particles = l("von"), particleChance = 0.05f, hyphenChance = 0.04f),
        "Itsuki" to Flavour(
            l("Hiroshi Kazuo Masaru Tetsuya Isamu Satoshi Minoru Osamu Susumu Goro Yoshio Takeshi Shigeru Hideo Kenzo Jiro Katsu Noboru"),
            l("Yuki Hana Sakura Aoi Mio Rin Haruka Emi Kaori Natsuki Yui Miyu Sayaka Nanami Akari Chiaki Ayumi Riko Misaki Kaede"),
            l("Takahashi Nakamura Kobayashi Yamamoto Matsuda Inoue Kimura Hayashi Shimizu Yamaguchi Saito Ikeda Hashimoto Ishikawa Maeda Fujita Okada Goto Hasegawa Murakami Kondo Ogawa Aoki Sakamoto"))
    )

    private val GENERIC = Flavour(l("Alex Sam Max Leo Nick Dan Tom Ben"), l("Anna Eva Mia Lea Nora Ida Ella Zoe"), l("Marsh Field Stone Brook Hill Lane"))

    private val NICKNAMES = l("Rocket Tank Ghost Smiler Rhino Wolf Magic Flash Chief Doc Tiger Bull Viking Hawk Rascal Shadow Ace Cannon Prof Stork Bambi Duke Lefty Turbo Ginger Tiny Bear Fox Panther Hammer Gentle Whippet Maestro Ox Sniper Wizard Kid Boss Cat")

    /** Substrings that must never appear in a generated name. */
    private val BLOCK = l("nig fag cunt shit rape nazi slut whor dick piss fuck")

    private fun flavour(nat: Int): Flavour = FLAVOUR[Names.nations[Math.floorMod(nat, Names.nations.size)].name] ?: GENERIC

    private fun <T> pick(list: List<T>, rnd: Random): T = list[rnd.nextInt(list.size)]

    private fun isVowel(c: Char) = c.lowercaseChar() in "aeiouy"

    private fun titleCase(s: String): String {
        val sb = StringBuilder(s.length)
        var up = true
        for (c in s) {
            sb.append(if (up) c.uppercaseChar() else c)
            up = c == '-' || c == '\'' || c == ' '
        }
        return sb.toString()
    }

    /** Joins two syllables without doubled seams ("Bell" + "lo" -> "Bello") and without a clashing double vowel. */
    private fun glue(a: String, b: String): String {
        if (b.isEmpty()) return a
        var y = b
        if (a.last().lowercaseChar() == y.first().lowercaseChar()) y = y.drop(1)
        if (y.isNotEmpty() && isVowel(a.last()) && isVowel(y.first()) && a.last().lowercaseChar() == y.first().lowercaseChar()) y = y.drop(1)
        return a + y
    }

    private fun syllableSurname(nd: NationDef, rnd: Random): String {
        val mid = if (rnd.nextInt(3) == 0) pick(nd.mid, rnd) else ""
        return titleCase(glue(glue(pick(nd.pre, rnd), mid), pick(nd.suf, rnd)))
    }

    private fun baseSurname(nat: Int, rnd: Random): String {
        val nd = Names.nations[Math.floorMod(nat, Names.nations.size)]
        val fl = flavour(nat)
        return if (rnd.nextFloat() < 0.55f) pick(fl.surnames, rnd) else syllableSurname(nd, rnd)
    }

    private fun classicShare(age: Int): Float = when {
        age >= 32 -> 0.65f
        age >= 27 -> 0.35f
        age >= 23 -> 0.15f
        else -> 0.05f
    }

    private fun clean(full: String): Boolean {
        val low = full.lowercase()
        if (full.length > 28 || full.length < 5) return false
        for (b in BLOCK) if (low.contains(b)) return false
        val letters = low.filter { it.isLetter() }
        if (Regex("[bcdfghjklmnpqrstvwxz]{5,}").containsMatchIn(letters)) return false
        if (Regex("(.)\\1\\1").containsMatchIn(letters)) return false
        return true
    }

    private fun build(nat: Int, rnd: Random, o: NameOptions): PersonName {
        val n = Math.floorMod(nat, Names.nations.size)
        val nd = Names.nations[n]
        val fl = flavour(n)
        // --- given name
        var given = if (o.gender == Gender.FEMALE) pick(fl.female, rnd)
        else pick(if (rnd.nextFloat() < classicShare(o.age)) fl.classic else nd.first, rnd)
        if (o.gender == Gender.MALE && rnd.nextFloat() < fl.hyphenGiven) {
            val second = pick(nd.first, rnd)
            if (second != given) given = given + "-" + second
        }
        // --- family name: sometimes from another nation (mixed heritage)
        var heritage = n
        var mixed = false
        if (rnd.nextFloat() < o.foreignChance) {
            val h = Names.pickNation(rnd)
            if (h != n) { heritage = h; mixed = true }
        }
        val hf = flavour(heritage)
        val hd = Names.nations[heritage]
        var pattern = if (mixed) "mixed" else "plain"
        var surname: String
        if (hf.patronymic != null && rnd.nextFloat() < hf.patronymicChance) {
            surname = titleCase(pick(hd.first, rnd).lowercase()) + hf.patronymic
            pattern = "patronymic"
        } else {
            surname = baseSurname(heritage, rnd)
        }
        if (o.gender == Gender.FEMALE) {
            for ((from, to) in hf.femaleEnd) if (surname.endsWith(from)) { surname = surname.dropLast(from.length) + to; break }
        }
        if (hf.doubleChance > 0f && rnd.nextFloat() < hf.doubleChance) {
            val second = baseSurname(heritage, rnd)
            if (second != surname) { surname = "$surname $second"; pattern = "double" }
        } else if (hf.hyphenChance > 0f && rnd.nextFloat() < hf.hyphenChance) {
            val second = baseSurname(heritage, rnd)
            if (second != surname) { surname = "$surname-$second"; pattern = "hyphen" }
        }
        if (hf.prefixes.isNotEmpty() && rnd.nextFloat() < hf.prefixChance) {
            surname = pick(hf.prefixes, rnd) + titleCase(surname)
            pattern = "prefix"
        } else if (hf.particles.isNotEmpty() && rnd.nextFloat() < hf.particleChance) {
            val part = pick(hf.particles, rnd)
            surname = "$part $surname"
            pattern = "particle"
        }
        val nick = if (o.nicknames && o.age >= 18 && rnd.nextFloat() < 0.06f) pick(NICKNAMES, rnd) else null
        return PersonName(given, surname, nick, o.gender, n, pattern)
    }

    /** Builds a name, re-rolling a few times if it trips the blocklist or the quality checks. */
    fun make(nat: Int, rnd: Random, o: NameOptions = NameOptions()): PersonName {
        repeat(8) {
            val p = build(nat, rnd, o)
            if (clean(p.full)) return p
        }
        return build(nat, rnd, o)
    }

    /** For staff, owners and anyone who is not always a man: draws a gender with the given female share. */
    fun randomGender(rnd: Random, femaleShare: Float): Gender = if (rnd.nextFloat() < femaleShare) Gender.FEMALE else Gender.MALE

    /** A handful of sample names for one nation: useful for tools and for checking the data by eye. */
    fun sample(nat: Int, seed: Long, count: Int, o: NameOptions = NameOptions()): List<PersonName> {
        val rnd = Random(seed)
        return List(count) { make(nat, rnd, o) }
    }

    /**
     * Renames players whose full name exactly matches an earlier player's, deterministically from the world seed and the
     * player id. Only does anything in a world that uses the richer generator. Returns how many were renamed.
     */
    fun fixDuplicates(w: World): Int {
        if (!w.richNames) return 0
        val seen = HashSet<String>()
        var fixed = 0
        for (p in w.players.values.sortedBy { it.id }) {
            if (seen.add(p.name)) continue
            for (attempt in 0 until 12) {
                val r = Random(Mix.h(w.seed, p.id.toLong(), 7900L, attempt.toLong()))
                val candidate = make(p.nation, r, NameOptions(age = p.age)).full
                if (seen.add(candidate)) { p.name = candidate; fixed++; break }
            }
        }
        return fixed
    }
}
