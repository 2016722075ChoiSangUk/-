package com.plateocr.httpapi;

public class ModelCar {
//        data = query_db(f"SELECT apartment.name as aname, user.address, plate, user.name, car.permit, car.updated FROM car, user, apartment WHERE car.uid=user.id AND apartment.id=user.aid AND plate=?",
    String apartmentName;
    String address;
    String plate;
    String name;
    String permit;
}
