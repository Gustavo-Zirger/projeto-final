package br.edu.projetofinal.reengagement;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Use: java -jar app.jar tracker|notifier");
        }
        switch (args[0]) {
            case "tracker" -> new TrackerService().run();
            case "notifier" -> new NotificationService().run();
            default -> throw new IllegalArgumentException(
                    "Servico desconhecido: " + args[0]);
        }
    }
}
