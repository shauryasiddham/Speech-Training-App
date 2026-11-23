package com.simplespeak.app.data

/** What kind of conversation the coach is having with him. */
sealed interface TalkMode {
    val title: String
    val emoji: String

    /** Free conversation on a topic. */
    data class Free(override val title: String, override val emoji: String, val opener: String) : TalkMode

    /** Real-life role-play: the coach plays the other person. */
    data class Scenario(
        override val title: String,
        override val emoji: String,
        val category: String,
        val role: String,
        val place: String,
        val goal: String,
        val opener: String,
    ) : TalkMode

    /** He explains a process or idea; graded on order and clarity. */
    data class Explain(override val title: String, override val emoji: String) : TalkMode {
        val opener: String get() = "Please explain to me ${title.replaceFirstChar { it.lowercase() }}. Start from the beginning."
    }
}

object TalkContent {
    fun freeTopics(interests: String): List<TalkMode.Free> = buildList {
        add(TalkMode.Free("Anything you like", "✨", "What would you like to talk about today?"))
        add(TalkMode.Free("Your day", "☀️", "How has your day been so far? Tell me what you have done."))
        add(TalkMode.Free("Your weekend", "🎉", "What did you do last weekend, or what are you planning for the next one?"))
        interests.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(3).forEach { i ->
            add(TalkMode.Free(i.replaceFirstChar { it.uppercase() }, "⭐", "I'd like to hear about $i. What do you like most about it?"))
        }
        add(TalkMode.Free("A film, show or game", "🎬", "Tell me about a film, show or game you like, and why you like it."))
        add(TalkMode.Free("Your opinion", "💭", "Is there something you have a strong opinion about? Tell me what you think and why."))
        add(TalkMode.Free("Plans and goals", "🎯", "What is something you would like to do or get better at in the future?"))
    }

    val scenarios = listOf(
        // Everyday errands
        TalkMode.Scenario("Order at a café", "☕", "Everyday errands", "a café worker", "a small café",
            "Order a drink and something to eat, and ask how much it costs.", "Hello, welcome. What can I get for you today?"),
        TalkMode.Scenario("Buy something in a shop", "🛍️", "Everyday errands", "a shop assistant", "a clothes shop",
            "Ask for a T-shirt in your size and colour, and ask if you can try it on.", "Hi there. Can I help you find something?"),
        TalkMode.Scenario("Ask for directions", "🗺️", "Everyday errands", "a person walking past in the street", "a busy street in town",
            "Ask how to get to the nearest train station and check you understood.", "Oh, hello. Are you looking for somewhere?"),
        TalkMode.Scenario("Buy a train ticket", "🚆", "Everyday errands", "a ticket office worker", "a train station ticket office",
            "Buy a return ticket to the city for today and ask which platform the train leaves from.", "Good morning. Where would you like to travel to?"),
        // Phone calls
        TalkMode.Scenario("Book a doctor's appointment", "🩺", "Phone calls", "a receptionist answering the phone", "a doctor's surgery",
            "Book an appointment, say briefly why you need it, and agree on a day and time.", "Good morning, Riverside Medical Centre. How can I help you?"),
        TalkMode.Scenario("Book a haircut", "💈", "Phone calls", "a hairdresser answering the phone", "a hair salon",
            "Book a haircut for this week, say what kind of haircut, and ask the price.", "Hello, Style Studio. How can I help?"),
        TalkMode.Scenario("Call to ask a question", "📞", "Phone calls", "a shop worker answering the phone", "a local sports shop",
            "Ask what time the shop opens on Saturday and whether they have football boots in stock.", "Hello, City Sports, how can I help you?"),
        // School and work
        TalkMode.Scenario("Ask a teacher for help", "🧑‍🏫", "School and work", "a teacher after class", "a classroom",
            "Explain which homework question you don't understand and ask for help.", "Hello. Did you want to speak to me about something?"),
        TalkMode.Scenario("Explain a problem to a manager", "💼", "School and work", "a manager at work", "an office",
            "Explain that you can't finish a task on time, say why, and suggest a solution.", "Hi, come in. What did you want to talk about?"),
        TalkMode.Scenario("A short interview", "🤝", "School and work", "an interviewer", "a job or college interview",
            "Answer the questions clearly and give an example of something you are good at.", "Thank you for coming in today. To start, can you tell me a little about yourself?"),
    )

    val explainTopics = listOf(
        TalkMode.Explain("How to make a cup of tea", "🍵"),
        TalkMode.Explain("How to make a sandwich", "🥪"),
        TalkMode.Explain("The rules of a game you like", "🎲"),
        TalkMode.Explain("How to get from your home to the shops", "🏪"),
        TalkMode.Explain("How to brush your teeth properly", "🪥"),
        TalkMode.Explain("How to cook your favourite meal", "🍳"),
        TalkMode.Explain("How to send a text message", "💬"),
        TalkMode.Explain("Why people need sleep", "😴"),
    )

    /** Things he might say to finish the conversation. */
    private val endings = Regex("\\b(bye|goodbye|good bye|see you|i'?m done|i am done|that'?s all|let'?s stop|i want to stop|it'?s over|the end)\\b", RegexOption.IGNORE_CASE)
    fun soundsLikeGoodbye(text: String) = endings.containsMatchIn(text)
}
