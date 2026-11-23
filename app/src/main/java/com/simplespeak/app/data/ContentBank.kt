package com.simplespeak.app.data

/**
 * Built-in practice content. Level 1 = short and simple, level 3 = longer sentences
 * with joining words. Items marked for an age group only appear for that group.
 */
object ContentBank {
    /** Levels 1–3 have built-in content; 4–6 are written fresh by the AI coach. */
    const val MAX_LEVEL = 6
    const val MAX_BUILT_IN_LEVEL = 3

    /** What each level means, used when asking the AI for new practice. */
    val levelDescriptions = mapOf(
        1 to "short simple sentences of 4 to 6 words in the present tense",
        2 to "sentences of 6 to 9 words using past or future tense",
        3 to "sentences of 9 to 12 words with one linking word such as because, so, but or when",
        4 to "sentences of 12 to 16 words with two clauses joined by although, unless, while, if or before",
        5 to "sentences of 16 to 22 words with relative clauses (who, which, where), conditionals or reported speech",
        6 to "two connected sentences (22 to 30 words in total) using perfect tenses, the passive voice and precise, advanced vocabulary",
    )

    data class JoinItem(val level: Int, val parts: List<String>, val answers: List<String>)
    data class SequenceItem(val title: String, val steps: List<Pair<String, String>>) // (emoji, short step) in the right order

    data class BuildItem(val level: Int, val sentence: String, val hint: String, val ages: Set<AgeGroup> = ALL)
    data class FixItem(val level: Int, val broken: String, val answers: List<String>, val ages: Set<AgeGroup> = ALL)
    data class Scene(val id: String, val emoji: String, val description: String, val level: Int)

    private val ALL = AgeGroup.entries.toSet()
    private val KIDS = setOf(AgeGroup.CHILD)
    private val TEEN_ADULT = setOf(AgeGroup.TEEN, AgeGroup.ADULT)
    private val ADULTS = setOf(AgeGroup.ADULT)

    val buildItems = listOf(
        // Level 1: 3–4 words
        BuildItem(1, "I like apples.", "🍎"),
        BuildItem(1, "The dog runs.", "🐕"),
        BuildItem(1, "She is happy.", "😊"),
        BuildItem(1, "We eat lunch.", "🥪"),
        BuildItem(1, "He reads books.", "📚"),
        BuildItem(1, "The cat sleeps.", "🐈"),
        BuildItem(1, "I want water.", "💧"),
        BuildItem(1, "They play outside.", "🌳"),
        BuildItem(1, "It is raining.", "🌧️"),
        BuildItem(1, "My bag is red.", "🎒"),
        BuildItem(1, "I am tired.", "😴"),
        BuildItem(1, "Where is my phone?", "📱", TEEN_ADULT),
        BuildItem(1, "I love my teddy.", "🧸", KIDS),
        // Level 2: 5–6 words
        BuildItem(2, "I want to go outside.", "🚪"),
        BuildItem(2, "The bus is very late.", "🚌"),
        BuildItem(2, "She is reading a book.", "📖"),
        BuildItem(2, "We went to the park.", "🏞️"),
        BuildItem(2, "Can I have some water?", "🥤"),
        BuildItem(2, "He feeds his dog every morning.", "🐶"),
        BuildItem(2, "I am going to the shop.", "🛒"),
        BuildItem(2, "Please pass me the salt.", "🧂"),
        BuildItem(2, "The weather is cold today.", "🧣"),
        BuildItem(2, "My brother likes playing games.", "🎮"),
        BuildItem(2, "I finished my homework early.", "✏️", setOf(AgeGroup.CHILD, AgeGroup.TEEN)),
        BuildItem(2, "I would like a coffee, please.", "☕", ADULTS),
        BuildItem(2, "Can we read a story?", "📕", KIDS),
        // Level 3: 7–9 words, joining words
        BuildItem(3, "I went to the shop because we needed milk.", "🥛"),
        BuildItem(3, "After school I like to draw pictures.", "🎨", setOf(AgeGroup.CHILD, AgeGroup.TEEN)),
        BuildItem(3, "Can you help me find my blue jacket?", "🧥"),
        BuildItem(3, "My friend and I watched a funny movie.", "🎬"),
        BuildItem(3, "I feel tired because I stayed up late.", "🌙"),
        BuildItem(3, "Tomorrow we are going to visit grandma.", "👵"),
        BuildItem(3, "First I brush my teeth and then I eat breakfast.", "🪥"),
        BuildItem(3, "The train was late so I waited.", "🚆"),
        BuildItem(3, "I would like to book an appointment for Monday.", "📅", ADULTS),
        BuildItem(3, "Could you tell me where the station is?", "🚉", TEEN_ADULT),
        BuildItem(3, "When it stops raining we can go outside.", "🌈"),
    )

    val fixItems = listOf(
        // Level 1
        FixItem(1, "Me want water.", listOf("I want water.")),
        FixItem(1, "Him is happy.", listOf("He is happy.")),
        FixItem(1, "The dog run.", listOf("The dog runs.", "The dog ran.")),
        FixItem(1, "I has a cat.", listOf("I have a cat.")),
        FixItem(1, "She are tired.", listOf("She is tired.")),
        FixItem(1, "They is here.", listOf("They are here.")),
        FixItem(1, "I eated lunch.", listOf("I ate lunch.")),
        FixItem(1, "We goed home.", listOf("We went home.")),
        FixItem(1, "I see two dog.", listOf("I see two dogs.")),
        FixItem(1, "Is cold today.", listOf("It is cold today.")),
        // Level 2
        FixItem(2, "Yesterday I go to the park.", listOf("Yesterday I went to the park.")),
        FixItem(2, "I want go to the shop.", listOf("I want to go to the shop.")),
        FixItem(2, "She don't like carrots.", listOf("She doesn't like carrots.", "She does not like carrots.")),
        FixItem(2, "Where you are going?", listOf("Where are you going?")),
        FixItem(2, "I have three book.", listOf("I have three books.")),
        FixItem(2, "He can to swim fast.", listOf("He can swim fast.")),
        FixItem(2, "Tomorrow I went to school.", listOf("Tomorrow I will go to school.", "Tomorrow I am going to school.", "Tomorrow I'm going to go to school.")),
        FixItem(2, "Can I has some juice?", listOf("Can I have some juice?")),
        FixItem(2, "The childs are playing.", listOf("The children are playing.")),
        FixItem(2, "Me and him played football.", listOf("He and I played football.", "Him and I played football.")),
        // Level 3
        FixItem(3, "I was late because the bus not come.", listOf("I was late because the bus did not come.", "I was late because the bus didn't come.")),
        FixItem(3, "Him and me went shop after school.", listOf("He and I went to the shop after school.")),
        FixItem(3, "When I will finish, I tell you.", listOf("When I finish, I will tell you.")),
        FixItem(3, "I didn't went to the party.", listOf("I didn't go to the party.", "I did not go to the party.")),
        FixItem(3, "Last week I have seen a movie.", listOf("Last week I saw a movie.")),
        FixItem(3, "I am agree with you.", listOf("I agree with you.")),
        FixItem(3, "Do you can help me with this?", listOf("Can you help me with this?")),
        FixItem(3, "If it will rain, we stay inside.", listOf("If it rains, we will stay inside.", "If it rains, we'll stay inside.")),
        FixItem(3, "She asked me what time is it.", listOf("She asked me what time it was.", "She asked me what time it is.")),
        FixItem(3, "I need to finishing this report today.", listOf("I need to finish this report today."), TEEN_ADULT),
    )

    val scenes = listOf(
        Scene("dog_park", "🐕 ⚽ 🌳", "A dog is playing with a ball in a park.", 1),
        Scene("ice_cream", "👧 🍦 ☀️", "A girl is eating an ice cream on a sunny day.", 1),
        Scene("cat_box", "🐈 📦", "A cat is sitting inside a cardboard box.", 1),
        Scene("bed_book", "🧒 📚 🛏️", "A child is reading a book in bed.", 1),
        Scene("chef", "👨‍🍳 🍝 🔥", "A cook is making pasta on the stove.", 2),
        Scene("bus_rain", "🚌 🌧️ ☂️", "A person with an umbrella is waiting for the bus in the rain.", 2),
        Scene("shopping", "🛒 🍎 🥛", "Someone is shopping for apples and milk at the supermarket.", 2),
        Scene("beach", "👫 🏖️ 🌊", "Two friends are walking on the beach next to the waves.", 2),
        Scene("birthday", "👵 🎂 🎉", "A family is having a birthday party for grandma with a cake.", 3),
        Scene("doctor", "🧑‍⚕️ 🤒 🩺", "A doctor is checking a person who feels sick.", 3),
        Scene("bike_lake", "🚴 🏞️ 🌤️", "A person is riding a bike next to a lake in the hills.", 3),
        Scene("night_house", "🏠 🌙 ⭐", "It is night time and the moon and stars are above a house.", 3),
    )

    val planTopics = listOf(
        "Something you did today",
        "Food you want to eat later",
        "A place you like to go",
        "Something you need help with",
        "Your favourite thing to do",
        "Someone you saw this week",
        "A plan for the weekend",
        "Something that made you feel happy",
    )

    val joinItems = listOf(
        JoinItem(1, listOf("I was hungry.", "I ate a sandwich."), listOf("I was hungry, so I ate a sandwich.", "I ate a sandwich because I was hungry.")),
        JoinItem(1, listOf("It was raining.", "We stayed inside."), listOf("It was raining, so we stayed inside.", "We stayed inside because it was raining.")),
        JoinItem(1, listOf("I like football.", "I like basketball."), listOf("I like football and basketball.")),
        JoinItem(2, listOf("I wanted to go out.", "I had too much homework."), listOf("I wanted to go out, but I had too much homework.")),
        JoinItem(2, listOf("The shop was closed.", "We went to a different one."), listOf("The shop was closed, so we went to a different one.")),
        JoinItem(2, listOf("I finished my work.", "I watched a film."), listOf("After I finished my work, I watched a film.", "I finished my work and then I watched a film.")),
        JoinItem(3, listOf("I was tired.", "I kept studying.", "The test was tomorrow."), listOf("Although I was tired, I kept studying because the test was tomorrow.")),
        JoinItem(3, listOf("The bus was late.", "I missed the start of the lesson.", "My teacher was not happy."), listOf("The bus was late, so I missed the start of the lesson and my teacher was not happy.")),
        JoinItem(3, listOf("You can come with us.", "You must be ready by six."), listOf("You can come with us if you are ready by six.", "You can come with us, but you must be ready by six.")),
    )

    val questions = mapOf(
        1 to listOf("What did you eat today?", "What is your favourite food?", "Where do you live?", "What do you like to do after school or work?", "What is the weather like today?"),
        2 to listOf("What did you do last weekend?", "What are you going to do tomorrow?", "Tell me about a place you like to visit.", "What is your favourite film or show, and why?", "Who is someone you spend a lot of time with?"),
        3 to listOf("Describe your perfect day from morning to night.", "What is something you are good at, and how did you learn it?", "Tell me about a problem you solved recently.", "If you could visit any country, where would you go and why?", "What would you change about your town and why?"),
        4 to listOf("Do you think phones should be allowed in schools? Give reasons.", "Explain how to make your favourite meal, step by step.", "What makes someone a good friend?"),
        5 to listOf("Should people work four days a week instead of five? Explain both sides.", "Describe a time you changed your mind about something.", "How has technology changed the way people talk to each other?"),
        6 to listOf("What is one rule you would make for the whole country, and what might go wrong?", "Explain a hobby to someone who has never heard of it.", "Is it better to be honest or to be kind? Give an example."),
    )

    val sequences = listOf(
        SequenceItem("Making toast", listOf("🍞" to "Take bread from the bag", "🔥" to "Put it in the toaster", "🧈" to "Spread butter on it", "😋" to "Eat the toast")),
        SequenceItem("Going to school", listOf("⏰" to "The alarm rings", "👕" to "Get dressed", "🚌" to "Catch the bus", "🏫" to "Arrive at school")),
        SequenceItem("Planting a seed", listOf("🪴" to "Fill a pot with soil", "🌰" to "Push in the seed", "💧" to "Water it every day", "🌱" to "A small plant grows")),
        SequenceItem("Buying a ticket", listOf("🚉" to "Walk into the station", "🧾" to "Choose a ticket on the machine", "💳" to "Pay with a card", "🚆" to "Get on the train")),
        SequenceItem("A rainy walk", listOf("🚶" to "Go out for a walk", "🌧️" to "It starts to rain", "☂️" to "Open the umbrella", "🏠" to "Walk home quickly")),
    )

    fun joinFor(level: Int) = joinItems.filter { it.level == level.coerceAtMost(MAX_BUILT_IN_LEVEL) }.ifEmpty { joinItems }
    fun questionsFor(level: Int) = questions[level] ?: questions.getValue(3)

    fun buildFor(level: Int, age: AgeGroup) = buildItems.filter { it.level == level.coerceAtMost(MAX_BUILT_IN_LEVEL) && age in it.ages }
    fun fixFor(level: Int, age: AgeGroup) = fixItems.filter { it.level == level.coerceAtMost(MAX_BUILT_IN_LEVEL) && age in it.ages }
    fun scenesFor(level: Int) = scenes.filter { it.level <= level }
}
