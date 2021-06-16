package tem.csdn.model

import tem.csdn.NoArg

@NoArg
data class TextFrameAction(val type: TextFrameActionType, val content: Any)