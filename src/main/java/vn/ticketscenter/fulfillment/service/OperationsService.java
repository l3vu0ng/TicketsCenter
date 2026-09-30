package vn.ticketscenter.fulfillment.service;
import vn.ticketscenter.config.persistence.*;import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.*;import vn.ticketscenter.fulfillment.repository.OperationsRepository;import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;import vn.ticketscenter.identity.service.AuthorizationService;import java.util.*;
public final class OperationsService {
 private final TransactionManager tx; private final OperationsRepository repo; public OperationsService(TransactionManager tx,OperationsRepository repo){this.tx=tx;this.repo=repo;}
 public CheckInResultView checkIn(AuthenticatedAccount a,UUID event,String code){if(a==null)throw new SecurityException("authenticated account required");if(event==null||code==null||code.isBlank())throw new IllegalArgumentException("eventId and ticketCode are required");return tx.execute(DatabasePrincipal.CHECK_IN,em->repo.checkIn(em,event,a.id(),code));}
 public List<RefundableTicket> refundable(AuthenticatedAccount a,UUID order){require(a);return tx.execute(DatabasePrincipal.BUYER,em->repo.refundable(em,order));}
 public RefundRequestResult request(AuthenticatedAccount a,UUID order,List<UUID> ids,String reason){require(a);if(order==null||ids==null||ids.isEmpty()||reason==null||reason.isBlank())throw new IllegalArgumentException("orderId, ticketIds and reason are required");String json=ids.stream().map(id->"\""+id+"\"").reduce((x,y)->x+","+y).map(x->"["+x+"]").orElse("[]");return tx.execute(DatabasePrincipal.BUYER,em->repo.requestRefund(em,order,a.id(),json,reason));}
 public String decide(AuthenticatedAccount a,UUID id,String decision,String reason){AuthorizationService.requireAdmin(a);return tx.execute(DatabasePrincipal.ADMIN,em->repo.decideRefund(em,id,a.id(),decision,reason));}
 private static void require(AuthenticatedAccount a){if(a==null)throw new SecurityException("authenticated account required");}
}
