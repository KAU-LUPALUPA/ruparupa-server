package com.example.demo.controller;

import com.example.demo.dto.PlazaDto;
import com.example.demo.service.PlazaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plazas")
@RequiredArgsConstructor
public class PlazaController {
    private final PlazaService plazaService;

    @PostMapping("/random/join")
    public ResponseEntity<PlazaDto.PlazaRoomResponse> joinRandomPlaza(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(plazaService.joinRandomPlaza(currentUid));
    }

    @PostMapping("/code/join")
    public ResponseEntity<PlazaDto.PlazaRoomResponse> joinPlazaByCode(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody PlazaDto.JoinByCodeRequest request
    ) {
        return ResponseEntity.ok(plazaService.joinPlazaByCode(currentUid, request.getCode()));
    }

    @GetMapping("/me/active")
    public ResponseEntity<PlazaDto.PlazaRoomResponse> getMyActivePlaza(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(plazaService.getCurrentPlaza(currentUid));
    }

    @GetMapping("/{plazaId}")
    public ResponseEntity<PlazaDto.PlazaRoomResponse> getPlazaSnapshot(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String plazaId
    ) {
        return ResponseEntity.ok(plazaService.getPlazaSnapshot(plazaId, currentUid));
    }

    @PostMapping("/{plazaId}/leave")
    public ResponseEntity<Void> leavePlaza(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String plazaId
    ) {
        plazaService.leavePlaza(plazaId, currentUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{plazaId}/messages")
    public ResponseEntity<PlazaDto.PlazaChatMessageEnvelopeResponse> sendPlazaMessage(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String plazaId,
            @RequestBody PlazaDto.MessageRequest request
    ) {
        return ResponseEntity.ok(plazaService.sendPlazaMessage(plazaId, currentUid, request.getText()));
    }
}
