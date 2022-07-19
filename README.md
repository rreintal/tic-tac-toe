
#Mis peab olemas olema?
* Java 17
* Google Gmail API 1.33
* Javax mail 1.5.1
* Gradle 2.3+
* Google Cloud projekt, kus Gmail API lubatud.

##Kuidas programmi alustad?

1. Esimese asjana tuleb teha Gmail API quickstart, et saada kätte oma token ja alla laadida credentials.json fail Google Cloudi projekti alt.
```
Link: https://developers.google.com/gmail/api/quickstart/java
```

2. Seadistada `application` klassi muutujad `TOKENS_DIRECTORY_PATH`, `CREDENTIALS_FILE_PATH` ja `DATABASE_PATH`.


3. Käivita programm.

##Kuidas mängida teise inimesega trips-traps-trulli?

```
Mängulaud
    
  y0 y1 y2
x0 -  -  -
x1 -  -  -
x2 -  -  -
```

###1. Mängija saadab meilile kirja formaadiga:
```
Play with: example@gmail.com
```

###2. Server saadab sellele mängijale mängukutse

```
example@gmail.com wants to play tic-tac-toe with you.
Reply with OK to start game.
```

###3. Mängija kellele tuli kutse, peab vastama samale kirjale
``
OK
``

###4. Server genereerib mängu ID ja saadab mõlemale mängijale kirja, et mäng on loodud ning hakkab nüüd.

###5. Mängija kellele saadetakse meil koos mängulauaga esimesena, see alustab
```
Your turn!
E | E | E |
E | E | E |
E | E | E |
```

###6. Käigu saab käia kui vastata kirjale formaadiga: 
```
Mark:(x,y)
```
Näiteks:
```
Mark:(1,2)
```
### Vale käigu puhul saadetakse sellekohane sõnum ning oodatakse, et mängija teeb uue käigu.
```
Wrong input!
Try again.
```


###7. Peale käigu käimist saadetakse laud teisele mängijale, ning oodatakse tema käiku.

###8. Kui keegi mängijatest on võitnud, saadetakse sellekohane sõnum, et kas sa võitsid või kaotasid.

# Mängu toimimse skeem on lisatud ka .png failina
<a href="https://github.com/rreintal/tic-tac-toe/blob/master/README/tictactoe-scheme.png">tictactoe-scheme.png</a>
