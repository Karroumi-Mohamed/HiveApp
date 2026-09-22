package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/customer-communications")
public class CustomerCommunicationAdminController {
  private final CustomerCommunicationAdminService service;

  @GetMapping
  public PageResponse<Publication> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.list(CommunicationService.page(page, size)));
  }

  @GetMapping("/recipients")
  public PageResponse<AccountChoice> choices(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.choices(search, CommunicationService.page(page, size)));
  }

  @GetMapping("/{id}")
  public Publication detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/selected-recipients")
  public List<AccountChoice> selectedRecipients(@PathVariable UUID id) {
    return service.selectedRecipients(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Publication create(@Valid @RequestBody Draft d) {
    return service.create(d);
  }

  @PutMapping("/{id}")
  public Publication edit(@PathVariable UUID id, @Valid @RequestBody Edit d) {
    return service.edit(id, d);
  }

  @PostMapping("/{id}/publish")
  public Publication publish(@PathVariable UUID id, @Valid @RequestBody Command c) {
    return service.publish(id, c);
  }

  @PostMapping("/{id}/cancel")
  public Publication cancel(@PathVariable UUID id, @Valid @RequestBody Command c) {
    return service.cancel(id, c);
  }

  @GetMapping("/{id}/results")
  public PageResponse<Recipient> recipients(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.recipients(id, CommunicationService.page(page, size)));
  }

  @PostMapping("/entries/{id}/retry-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retry(@PathVariable UUID id) {
    service.retry(id);
  }

  @GetMapping("/entries/{id}/replies")
  public PageResponse<Reply> thread(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.thread(id, CommunicationService.page(page, size)));
  }

  @PostMapping("/entries/{id}/replies")
  public Reply reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest r) {
    return service.reply(id, r);
  }

  @PostMapping("/entries/{id}/close")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void close(@PathVariable UUID id, @RequestParam boolean closed) {
    service.close(id, closed);
  }
}
