package tem.csdn.intf

import tem.csdn.model.TextFrameAction

interface TextFrameActionConvertable {
    fun toTextFrameAction(): TextFrameAction
}