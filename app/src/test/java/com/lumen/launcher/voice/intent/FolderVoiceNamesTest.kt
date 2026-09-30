package com.lumen.launcher.voice.intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class FolderVoiceNamesTest {
    @Test fun savedFolderDoesNotRequireFolderKeyword() {
        listOf("add WhatsApp to Social", "put WhatsApp in my Social folder", "move WhatsApp into folder called Social").forEach {
            val intent = VoiceQueryRouter.route(listOf(it), listOf("WhatsApp"), listOf("Social"))
            assertThat(intent.action).isEqualTo(VoiceAction.ADD_TO_FOLDER)
            assertThat(intent.folderName).isEqualTo("social")
            assertThat(intent.appName).isEqualTo("whatsapp")
        }
    }
    @Test fun unknownNameDoesNotBecomeFolderRequest() {
        assertThat(FolderVoiceNames.normalize("add WhatsApp to dock", listOf("Social"))).isEqualTo("add WhatsApp to dock")
    }
}
