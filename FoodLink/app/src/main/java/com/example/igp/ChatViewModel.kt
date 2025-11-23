package com.example.igp

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel : ViewModel() {

    val messageList by lazy {
        mutableStateListOf<MessageModelchat>()
    }

    private val generativeModel: GenerativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = Constants.apiKey
    )

    // Context for the AI model
    private val systemContext = """
        You are a helpful assistant for FoodLink, a food donation app that connects donors with food banks 
        and manages food delivery. Focus on providing accurate information about food donation processes, 
        safety guidelines, and app features. If you're unsure about specific app functionality, stick to 
        general food donation best practices. Be concise, friendly, and encouraging.
    """.trimIndent()

    // Expanded predefined responses
    private val predefinedResponses = mapOf(
        "how can i donate food" to "To donate food, simply register in the app and enter your address. Then, you can organize a donation and have the food picked up by a food delivery person, who will deliver it to a nearby food bank or distribution center.",
        "do i need to hand over the food in person" to "No, you don't need to hand over the food in person. Once you organize the donation in the app, a food delivery person will pick up the food from your provided address and deliver it to the food bank or distribution center.",
        "what if i need to change the donation address" to "No problem! If you need to change the address, you can update it in the app anytime before the food delivery person arrives to pick it up. Just make sure to update the address in time so the food can be picked up and delivered correctly.",
        "what type of food can i donate" to "You can donate all non-perishable foods like rice, lentils, pasta, canned goods, and packaged foods. Make sure that the food is undamaged and still within its expiration date.",
        "do i need to pack the food" to "Yes, it's recommended to pack the food securely to prevent damage during transport. If possible, pack the food in suitable containers or bags.",
        "does it cost anything to donate food" to "Donating food through the app is free. The pickup and delivery of the food by the food delivery person are supported by the app at no cost to you.",
        "how can i be sure the food will actually reach the food bank" to "Our app works only with trusted food delivery persons who ensure that the food is delivered directly to the selected food bank or distribution center. You can track the delivery status in the app.",
        "what if there are no food delivery persons available near me" to "If no food delivery persons are available near you, you will receive a notification. We will do our best to find a nearby delivery person as soon as possible. We are constantly working to expand coverage.",
        "how do i register for the app" to "To register, simply download the app and enter your email address and a password. After that, you can add your address and start donating.",
        "can i donate food regularly" to "Yes, you can donate food regularly! The app allows you to set up a recurring donation so you can organize regular deliveries to a food bank or distribution center.",
        "can i donate food to organizations other than food banks" to "Yes, you can send your donations to various organizations, such as local food distribution centers or shelters. The app provides a list of partner organizations you can donate to.",
        "how do i know where my food was delivered" to "After your food has been picked up, you will receive a confirmation with details of the delivery. You can track the progress of the delivery in the app and see which food bank or organization received the food.",
        "can i donate warm meals" to "Currently, we focus on donating non-perishable food. Warm meals cannot be donated through the app at this time, but we will inform you if this feature becomes available in the future.",
        "what happens if i want to cancel my donation" to "You can cancel your donation at any time, as long as the food delivery person hasn't already started their way to pick up the food. Once the food is picked up, it can no longer be canceled.",
        "can i see who received my donation" to "Yes, after the delivery, you can see the name of the food bank or organization that received your donation in the app. In some cases, photos or confirmations of the donation may also be provided.",
        "what happens if there is an issue with the pickup or delivery" to "If there is an issue with the pickup or delivery, please contact us through the app. Our support team will promptly assist you to ensure that your donation is handled correctly.",
        "can i rate the food delivery person" to "Yes, after the delivery, you can rate the food delivery person in the app. Your feedback helps us improve the service and ensures that all donations are delivered safely and on time.",
        "is the app secure" to "Yes, we place a high priority on privacy and security. Your personal data is protected and not shared with third parties. All payments and transactions are securely processed.",
        // Adding new predefined Q&A pairs
        "how much food can i donate at once" to "There is no strict limit on the amount of food you can donate. However, for practical purposes, we recommend that single donations should be manageable for one delivery person to carry safely. For large donations (over 50kg), please contact our support team through the app to arrange special transportation.",
        "can i donate groceries that are close to their expiration date" to "Yes, you can donate groceries that are approaching their expiration date, but they must still be within the safe consumption period. For packaged foods, they should have at least 1 week remaining before the expiration date.",
        "are there any rewards for regular donors" to "Yes! FoodLink has a donor recognition program. Regular donors earn badges and achievement certificates. Additionally, we provide monthly impact reports showing how many meals your donations have helped provide.",
        "can businesses use the app" to "Absolutely! We welcome businesses such as restaurants, grocery stores, and food manufacturers to donate through our app. Business accounts receive additional features like scheduled recurring pickups and bulk donation management."
    )

    private fun normalizeQuestion(question: String): String {
        return question.trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove special characters
            .replace(Regex("\\s+"), " ") // Normalize whitespace
    }

    private fun findBestMatch(question: String): String? {
        val normalizedInput = normalizeQuestion(question)

        // Direct match
        predefinedResponses[normalizedInput]?.let { return it }

        // Fuzzy match - check if the question contains key phrases
        predefinedResponses.keys.forEach { key ->
            if (normalizedInput.contains(key)) {
                return predefinedResponses[key]
            }
        }

        return null
    }

    fun sendMessage(question: String) {
        viewModelScope.launch {
            try {
                // Add user's message to the list
                messageList.add(MessageModelchat(question, "user"))

                // Check for predefined response
                val predefinedAnswer = findBestMatch(question)

                if (predefinedAnswer != null) {
                    messageList.add(MessageModelchat(predefinedAnswer, "model"))
                } else {
                    // Show typing indicator
                    messageList.add(MessageModelchat("Typing....", "model"))

                    // Initialize chat with context
                    val chat = generativeModel.startChat(
                        history = listOf(content("system") { text(systemContext) }) +
                                messageList.dropLast(1).map {
                                    content(it.role) { text(it.message) }
                                }
                    )

                    // Get AI response
                    val response = chat.sendMessage(question)

                    // Remove typing indicator and add response
                    messageList.removeLast()
                    messageList.add(MessageModelchat(response.text?.toString() ?: "Sorry, I couldn't process that request.", "model"))
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
                // Remove typing indicator if it exists
                if (messageList.lastOrNull()?.message == "Typing....") {
                    messageList.removeLast()
                }
                messageList.add(MessageModelchat("I apologize, but I'm having trouble processing your request. Please try again later.", "model"))
            }
        }
    }
}