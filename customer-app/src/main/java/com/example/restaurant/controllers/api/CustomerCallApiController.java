package com.example.restaurant.controllers.api;

import com.example.restaurant.models.Client;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.WaiterCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Optional;

@RestController
@RequestMapping("/api/calls")
public class CustomerCallApiController {

    private final WaiterCallService waiterCallService;
    private final ClientService clientService;

    @Autowired
    public CustomerCallApiController(WaiterCallService waiterCallService, ClientService clientService) {
        this.waiterCallService = waiterCallService;
        this.clientService = clientService;
    }

    @PostMapping("/create")
    public ResponseEntity<String> createCall(@RequestParam String tableNumber, Principal principal) {
        Client client = null;
        if (principal != null) {
            Optional<Client> clientOpt = clientService.getClientByUsername(principal.getName());
            if (clientOpt.isPresent()) {
                client = clientOpt.get();
            }
        }

        waiterCallService.createCall(tableNumber, client);
        return ResponseEntity.ok("Официант вызван");
    }
}