package engines.interceptors

import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import com.vdurmont.emoji.EmojiParser
import models.events.TextResponse

/**
  * Converts emojis to names
  *
  * Created by markmo on 20/12/2016.
  */
trait EmojiInterceptor {
  this: ReceivePipeline =>

  pipelineInner {
    case ev@TextResponse(_, _, text, _) =>
      Inner(ev.copy(text = EmojiParser.parseToAliases(text)))
  }

}
