package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/communications")
public class ClientCommunicationController {
  private final AccountShellService service;

  @GetMapping
  public PageResponse<Item> list(
      @RequestParam(required = false) Kind kind,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        service.communicationInbox(kind, archived, unread, CommunicationService.page(page, size)));
  }

  @GetMapping("/{id}")
  public Item detail(@PathVariable UUID id) {
    return service.communicationDetail(id);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@PathVariable UUID id) {
    service.readCommunication(id);
  }

  @PostMapping("/{id}/acknowledge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acknowledge(@PathVariable UUID id) {
    service.acknowledgeCommunication(id);
  }

  @PostMapping("/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(
      @PathVariable UUID id, @RequestParam(defaultValue = "true") boolean archived) {
    service.archiveCommunication(id, archived);
  }

  @GetMapping("/{id}/replies")
  public PageResponse<Reply> thread(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        service.communicationThread(id, CommunicationService.page(page, size)));
  }

  @PostMapping("/{id}/replies")
  public Reply reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest r) {
    return service.replyCommunication(id, r);
  }

  @GetMapping("/preferences")
  public Preference preferences() {
    return service.communicationPreference();
  }

  @PutMapping("/preferences")
  public Preference preferences(@Valid @RequestBody Preference p) {
    return service.updateCommunicationPreference(p);
  }
}
