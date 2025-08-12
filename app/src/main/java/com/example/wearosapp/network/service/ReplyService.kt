package com.example.wearosapp.network.service

import com.example.wearosapp.network.model.request.ReplyRequest

interface ReplyService{

    suspend fun reply(request: ReplyRequest)

}
