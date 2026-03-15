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

        if(data.street() != null) {
            this.street = data.street();
        }

        if(data.neighborhood() != null) {
            this.neighborhood = data.neighborhood();
        }

        if(data.zipCode() != null) {
            this.zipCode = data.zipCode();
        }

        if(data.number() != null) {
            this.number = data.number();
        }

        if(data.complement() != null) {
            this.complement = data.complement();
        }

        if(data.city() != null) {
            this.city = data.city();
        }

        if(data.state() != null) {
            this.state = data.state();
        }

    }
}