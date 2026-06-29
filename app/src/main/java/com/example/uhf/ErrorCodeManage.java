package com.example.uhf;

import com.rscja.deviceapi.UhfBase;

public class ErrorCodeManage {
    public static String getMessage(int errorCode){
         switch (errorCode){
             case UhfBase.ErrorCode.ERROR_NO_TAG:
                 return "Tag not found!";
             case UhfBase.ErrorCode.ERROR_INSUFFICIENT_PRIVILEGES:
                 return "No access permission!";
             case UhfBase.ErrorCode.ERROR_MEMORY_OVERRUN:
                 return "Memory overrun!";
             case UhfBase.ErrorCode.ERROR_MEMORY_LOCK:
                 return "Memory locked!";
             case UhfBase.ErrorCode.ERROR_TAG_NO_REPLY:
                 return "Tag not responding!";
             case UhfBase.ErrorCode.ERROR_PASSWORD_IS_INCORRECT:
                 return "Incorrect password!";
             case UhfBase.ErrorCode.ERROR_RESPONSE_BUFFER_OVERFLOW:
                 return "Buffer overflow!";
             case UhfBase.ErrorCode.ERROR_NO_ENOUGH_POWER_ON_TAG:
                 return "Insufficient tag power!";
             case UhfBase.ErrorCode.ERROR_OPERATION_FAILED:
                 return "Operation failed!";
                 default:
                     return "Failed!";

         }
    }
}
