package br.com.resenha.Resenha.model.address;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    private String street;
    private String neighborhood;
    private String zipCode;
    private String number;
    private String complement;
    private String city;
    private String state;

    public Address(DataAddress data) {
        this.street = data.street();
        this.neighborhood = data.neighborhood();
        this.zipCode = data.zipCode();
        this.number = data.number();
        this.complement = data.complement();
        this.city = data.city();
        this.state = data.state();
    }

    public void updateInformation(DataAddress data) {

        if(this.street != null) {
            this.street = data.street();
        }

        if(this.neighborhood != null) {
            this.neighborhood = data.neighborhood();
        }

        if(this.zipCode != null) {
            this.zipCode = data.zipCode();
        }

        if(this.number != null) {
            this.number = data.number();
        }

        if(this.complement != null) {
            this.complement = data.complement();
        }

        if(this.city != null) {
            this.city = data.city();
        }

        if(this.state != null) {
            this.state = data.state();
        }

    }
}