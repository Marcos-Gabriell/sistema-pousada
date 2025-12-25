/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}", // Isso garante que Tailwind olhe seus arquivos
  ],
  theme: {
    extend: {
      colors: {
        brejo: '#2D6A4F',
        amarelo: '#FFD60A',
        branco: '#FFFFFF',        
        'cinza-claro': '#F6F6F6',
        'cinza-medio': '#A0A0A0',
        preto: '#22223B',
        azulbrejo: '#233642',
        sucesso: '#38B000',
        alerta: '#FFD60A',
        erro: '#E63946',
        info: '#457B9D'
      }
    }
  },
  plugins: [],
}
