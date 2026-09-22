package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notifications")
public class OperatorNotificationController {
  private final OperatorNotificationService service;

  @GetMapping
  public PageResponse<Item> inbox(
      @RequestParam(required = false) Kind kind,
      @RequestParam(required = false) Topic topic,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        service.inbox(kind, topic, archived, unread, CommunicationService.page(page, size)));
  }

  @GetMapping("/summary")
  public InboxSummary summary() {
    return new InboxSummary(
        service.inbox(null, null, false, true, CommunicationService.page(0, 1)).getTotalElements());
  }

  @GetMapping("/preferences")
  public java.util.List<NotificationSetting> settings() {
    return service.settings();
  }

  @PutMapping("/preferences")
  public NotificationSetting setting(@Valid @RequestBody NotificationSetting setting) {
    return service.setting(setting);
  }

  @GetMapping("/{id}")
  public Item detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@PathVariable UUID id) {
    service.read(id);
  }

  @PostMapping("/{id}/acknowledge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acknowledge(@PathVariable UUID id) {
    service.acknowledge(id);
  }

  @PostMapping("/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(
      @PathVariable UUID id, @RequestParam(defaultValue = "true") boolean archived) {
    service.archive(id, archived);
  }

  @GetMapping("/delivery")
  public PageResponse<OperatorNotificationService.EventResult> delivery(
      @RequestParam(required = false) NotificationEvent.State state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.events(state, CommunicationService.page(page, size)));
  }

  @PostMapping("/delivery/{id}/retry")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retry(@PathVariable UUID id, @RequestBody @Valid Command command) {
    service.retry(id, command);
  }

  @GetMapping("/delivery/emails")
  public PageResponse<OperatorNotificationService.EmailResult> emails(
      @RequestParam(required = false)
          com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery state,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.emailResults(state, CommunicationService.page(page, size)));
  }

  @PostMapping("/delivery/emails/{id}/retry")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retryEmail(@PathVariable UUID id, @RequestBody @Valid Command command) {
    service.retryEmail(id, command);
  }
}
