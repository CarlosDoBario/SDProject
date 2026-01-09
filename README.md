# SDProject

Para correr o projeto: 
  Compilar o projeto: javac -d bin --source-path src src/server/*.java src/client/*.java src/common/*.java src/testes/*.java 
  - Em 3 terminais :
    - Em 1 terminal, iniciar o servidor: java -cp bin server.MainServer
    - Em 2 terminais separados, para 2 clientes: java -cp bin ui.ConsoleUI

Para correr os testes:
  1) Iniciar o servidor 
  2) Teste de Stress (Escalabilidade): java -cp bin(dependendo do nome da pasta) testes.TesteStress
  3) Teste de Robustez: java -cp bintestes.TesteRobustez
  4) Teste de persistência: java -cp bintestes.TestePersistencia
  5) Teste de Filtragem java -cp bintestes.TesteFiltragem 
