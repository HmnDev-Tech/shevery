"use client"

import React, { useState } from "react"

/* -------------------------------------------------------------------------- */
/*  M3 Expressive Parametric Polygons (from Illustrateris / M3 Shapes)        */
/* -------------------------------------------------------------------------- */

export const M3_POLYGON_12_LOBE =
  "M50.00,0.00L51.31,0.16L52.59,0.64L53.83,1.39L55.01,2.34L56.13,3.40L57.21,4.49L58.24,5.52L59.27,6.40L60.31,7.07L61.39,7.50L62.54,7.67L63.77,7.61L65.10,7.36L66.51,6.99L67.99,6.58L69.49,6.22L70.99,5.99L72.44,5.96L73.79,6.19L75.00,6.70L76.05,7.49L76.92,8.55L77.62,9.81L78.17,11.23L78.61,12.71L78.99,14.19L79.38,15.60L79.83,16.88L80.39,17.98L81.11,18.89L82.02,19.61L83.12,20.17L84.40,20.62L85.81,21.01L87.29,21.39L88.77,21.83L90.19,22.38L91.45,23.08L92.51,23.95L93.30,25.00L93.81,26.21L94.04,27.56L94.01,29.01L93.78,30.51L93.42,32.01L93.01,33.49L92.64,34.90L92.39,36.23L92.33,37.46L92.50,38.61L92.93,39.69L93.60,40.73L94.48,41.76L95.51,42.79L96.60,43.87L97.66,44.99L98.61,46.17L99.36,47.41L99.84,48.69L100.00,50.00L99.84,51.31L99.36,52.59L98.61,53.83L97.66,55.01L96.60,56.13L95.51,57.21L94.48,58.24L93.60,59.27L92.93,60.31L92.50,61.39L92.33,62.54L92.39,63.77L92.64,65.10L93.01,66.51L93.42,67.99L93.78,69.49L94.01,70.99L94.04,72.44L93.81,73.79L93.30,75.00L92.51,76.05L91.45,76.92L90.19,77.62L88.77,78.17L87.29,78.61L85.81,78.99L84.40,79.38L83.12,79.83L82.02,80.39L81.11,81.11L80.39,82.02L79.83,83.12L79.38,84.40L78.99,85.81L78.61,87.29L78.17,88.77L77.62,90.19L76.92,91.45L76.05,92.51L75.00,93.30L73.79,93.81L72.44,94.04L70.99,94.01L69.49,93.78L67.99,93.42L66.51,93.01L65.10,92.64L63.77,92.39L62.54,92.33L61.39,92.50L60.31,92.93L59.27,93.60L58.24,94.48L57.21,95.51L56.13,96.60L55.01,97.66L53.83,98.61L52.59,99.36L51.31,99.84L50.00,100.00L48.69,99.84L47.41,99.36L46.17,98.61L44.99,97.66L43.87,96.60L42.79,95.51L41.76,94.48L40.73,93.60L39.69,92.93L38.61,92.50L37.46,92.33L36.23,92.39L34.90,92.64L33.49,93.01L32.01,93.42L30.51,93.78L29.01,94.01L27.56,94.04L26.21,93.81L25.00,93.30L23.95,92.51L23.08,91.45L22.38,90.19L21.83,88.77L21.39,87.29L21.01,85.81L20.62,84.40L20.17,83.12L19.61,82.02L18.89,81.11L17.98,80.39L16.88,79.83L15.60,79.38L14.19,78.99L12.71,78.61L11.23,78.17L9.81,77.62L8.55,76.92L7.49,76.05L6.70,75.00L6.19,73.79L5.96,72.44L5.99,70.99L6.22,69.49L6.58,67.99L6.99,66.51L7.36,65.10L7.61,63.77L7.67,62.54L7.50,61.39L7.07,60.31L6.40,59.27L5.52,58.24L4.49,57.21L3.40,56.13L2.34,55.01L1.39,53.83L0.64,52.59L0.16,51.31L0.00,50.00L0.16,48.69L0.64,47.41L1.39,46.17L2.34,44.99L3.40,43.87L4.49,42.79L5.52,41.76L6.40,40.73L7.07,39.69L7.50,38.61L7.67,37.46L7.61,36.23L7.36,34.90L6.99,33.49L6.58,32.01L6.22,30.51L5.99,29.01L5.96,27.56L6.19,26.21L6.70,25.00L7.49,23.95L8.55,23.08L9.81,22.38L11.23,21.83L12.71,21.39L14.19,21.01L15.60,20.62L16.88,20.17L17.98,19.61L18.89,18.89L19.61,17.98L20.17,16.88L20.62,15.60L21.01,14.19L21.39,12.71L21.83,11.23L22.38,9.81L23.08,8.55L23.95,7.49L25.00,6.70L26.21,6.19L27.56,5.96L29.01,5.99L30.51,6.22L32.01,6.58L33.49,6.99L34.90,7.36L36.23,7.61L37.46,7.67L38.61,7.50L39.69,7.07L40.73,6.40L41.76,5.52L42.79,4.49L43.87,3.40L44.99,2.34L46.17,1.39L47.41,0.64L48.69,0.16L50.00,0.00Z"

export const M3_POLYGON_4_PINCH =
  "M50.00,0.00L51.31,0.08L52.60,0.31L53.88,0.69L55.13,1.22L56.33,1.89L57.49,2.69L58.60,3.62L59.64,4.65L60.61,5.79L61.52,7.02L62.35,8.32L63.10,9.68L63.78,11.08L64.39,12.52L64.92,13.97L65.40,15.42L65.81,16.86L66.16,18.28L66.47,19.66L66.75,20.99L67.00,22.26L67.23,23.46L67.46,24.60L67.69,25.65L67.94,26.62L68.22,27.50L68.53,28.30L68.90,29.01L69.32,29.65L69.80,30.20L70.35,30.68L70.99,31.10L71.70,31.47L72.50,31.78L73.38,32.06L74.35,32.31L75.40,32.54L76.54,32.77L77.74,33.00L79.01,33.25L80.34,33.53L81.72,33.84L83.14,34.19L84.58,34.60L86.03,35.08L87.48,35.61L88.92,36.22L90.32,36.90L91.68,37.65L92.98,38.48L94.21,39.39L95.35,40.36L96.38,41.40L97.31,42.51L98.11,43.67L98.78,44.87L99.31,46.12L99.69,47.40L99.92,48.69L100.00,50.00L99.92,51.31L99.69,52.60L99.31,53.88L98.78,55.13L98.11,56.33L97.31,57.49L96.38,58.60L95.35,59.64L94.21,60.61L92.98,61.52L91.68,62.35L90.32,63.10L88.92,63.78L87.48,64.39L86.03,64.92L84.58,65.40L83.14,65.81L81.72,66.16L80.34,66.47L79.01,66.75L77.74,67.00L76.54,67.23L75.40,67.46L74.35,67.69L73.38,67.94L72.50,68.22L71.70,68.53L70.99,68.90L70.35,69.32L69.80,69.80L69.32,70.35L68.90,70.99L68.53,71.70L68.22,72.50L67.94,73.38L67.69,74.35L67.46,75.40L67.23,76.54L67.00,77.74L66.75,79.01L66.47,80.34L66.16,81.72L65.81,83.14L65.40,84.58L64.92,86.03L64.39,87.48L63.78,88.92L63.10,90.32L62.35,91.68L61.52,92.98L60.61,94.21L59.64,95.35L58.60,96.38L57.49,97.31L56.33,98.11L55.13,98.78L53.88,99.31L52.60,99.69L51.31,99.92L50.00,100.00L48.69,99.92L47.40,99.69L46.12,99.31L44.87,98.78L43.67,98.11L42.51,97.31L41.40,96.38L40.36,95.35L39.39,94.21L38.48,92.98L37.65,91.68L36.90,90.32L36.22,88.92L35.61,87.48L35.08,86.03L34.60,84.58L34.19,83.14L33.84,81.72L33.53,80.34L33.25,79.01L33.00,77.74L32.77,76.54L32.54,75.40L32.31,74.35L32.06,73.38L31.78,72.50L31.47,71.70L31.10,70.99L30.68,70.35L30.20,69.80L29.65,69.32L29.01,68.90L28.30,68.53L27.50,68.22L26.62,67.94L25.65,67.69L24.60,67.46L23.46,67.23L22.26,67.00L20.99,66.75L19.66,66.47L18.28,66.16L16.86,65.81L15.42,65.40L13.97,64.92L12.52,64.39L11.08,63.78L9.68,63.10L8.32,62.35L7.02,61.52L5.79,60.61L4.65,59.64L3.62,58.60L2.69,57.49L1.89,56.33L1.22,55.13L0.69,53.88L0.31,52.60L0.08,51.31L0.00,50.00L0.08,48.69L0.31,47.40L0.69,46.12L1.22,44.87L1.89,43.67L2.69,42.51L3.62,41.40L4.65,40.36L5.79,39.39L7.02,38.48L8.32,37.65L9.68,36.90L11.08,36.22L12.52,35.61L13.97,35.08L15.42,34.60L16.86,34.19L18.28,33.84L19.66,33.53L20.99,33.25L22.26,33.00L23.46,32.77L24.60,32.54L25.65,32.31L26.62,32.06L27.50,31.78L28.30,31.47L29.01,31.10L29.65,30.68L30.20,30.20L30.68,29.65L31.10,29.01L31.47,28.30L31.78,27.50L32.06,26.62L32.31,25.65L32.54,24.60L32.77,23.46L33.00,22.26L33.25,20.99L33.53,19.66L33.84,18.28L34.19,16.86L34.60,15.42L35.08,13.97L35.61,12.52L36.22,11.08L36.90,9.68L37.65,8.32L38.48,7.02L39.39,5.79L40.36,4.65L41.40,3.62L42.51,2.69L43.67,1.89L44.87,1.22L46.12,0.69L47.40,0.31L48.69,0.08L50.00,0.00Z"

export const M3_POLYGON_8_FLOWER =
  "M50.00,0.00L51.31,0.13L52.59,0.50L53.85,1.11L55.05,1.91L56.20,2.88L57.29,3.95L58.33,5.08L59.31,6.21L60.25,7.30L61.17,8.30L62.10,9.16L63.04,9.87L64.02,10.40L65.07,10.74L66.19,10.92L67.39,10.93L68.69,10.83L70.06,10.63L71.50,10.40L73.00,10.16L74.52,9.98L76.04,9.90L77.53,9.95L78.94,10.17L80.25,10.57L81.44,11.18L82.46,11.99L83.33,12.99L84.01,14.16L84.53,15.47L84.88,16.90L85.10,18.39L85.22,19.92L85.26,21.45L85.28,22.93L85.31,24.34L85.40,25.67L85.59,26.89L85.91,28.00L86.37,29.00L87.01,29.91L87.81,30.73L88.77,31.51L89.88,32.25L91.08,32.98L92.36,33.74L93.66,34.54L94.92,35.40L96.11,36.34L97.16,37.36L98.05,38.47L98.72,39.65L99.15,40.89L99.34,42.19L99.27,43.51L98.97,44.85L98.45,46.19L97.75,47.50L96.92,48.77L96.00,50.00L95.05,51.18L94.12,52.31L93.27,53.41L92.53,54.47L91.94,55.52L91.53,56.58L91.31,57.66L91.27,58.77L91.41,59.94L91.70,61.17L92.10,62.47L92.57,63.83L93.07,65.25L93.53,66.71L93.91,68.19L94.17,69.67L94.26,71.11L94.16,72.50L93.84,73.81L93.30,75.00L92.54,76.07L91.57,76.99L90.42,77.78L89.12,78.42L87.71,78.93L86.24,79.34L84.74,79.67L83.27,79.95L81.85,80.23L80.53,80.53L79.32,80.89L78.23,81.36L77.28,81.95L76.46,82.68L75.75,83.56L75.14,84.60L74.58,85.77L74.06,87.06L73.55,88.43L73.00,89.84L72.39,91.25L71.71,92.60L70.92,93.86L70.03,94.98L69.02,95.91L67.90,96.63L66.69,97.12L65.39,97.37L64.03,97.38L62.64,97.16L61.23,96.76L59.82,96.20L58.44,95.54L57.10,94.82L55.80,94.09L54.56,93.41L53.37,92.83L52.22,92.38L51.10,92.10L50.00,92.00L48.90,92.10L47.78,92.38L46.63,92.83L45.44,93.41L44.20,94.09L42.90,94.82L41.56,95.54L40.18,96.20L38.77,96.76L37.36,97.16L35.97,97.38L34.61,97.37L33.31,97.12L32.10,96.63L30.98,95.91L29.97,94.98L29.08,93.86L28.29,92.60L27.61,91.25L27.00,89.84L26.45,88.43L25.94,87.06L25.42,85.77L24.86,84.60L24.25,83.56L23.54,82.68L22.72,81.95L21.77,81.36L20.68,80.89L19.47,80.53L18.15,80.23L16.73,79.95L15.26,79.67L13.76,79.34L12.29,78.93L10.88,78.42L9.58,77.78L8.43,76.99L7.46,76.07L6.70,75.00L6.16,73.81L5.84,72.50L5.74,71.11L5.83,69.67L6.09,68.19L6.47,66.71L6.93,65.25L7.43,63.83L7.90,62.47L8.30,61.17L8.59,59.94L8.73,58.77L8.69,57.66L8.47,56.58L8.06,55.52L7.47,54.47L6.73,53.41L5.88,52.31L4.95,51.18L4.00,50.00L3.08,48.77L2.25,47.50L1.55,46.19L1.03,44.85L0.73,43.51L0.66,42.19L0.85,40.89L1.28,39.65L1.95,38.47L2.84,37.36L3.89,36.34L5.08,35.40L6.34,34.54L7.64,33.74L8.92,32.98L10.12,32.25L11.23,31.51L12.19,30.73L12.99,29.91L13.63,29.00L14.09,28.00L14.41,26.89L14.60,25.67L14.69,24.34L14.72,22.93L14.74,21.45L14.78,19.92L14.90,18.39L15.12,16.90L15.47,15.47L15.99,14.16L16.67,12.99L17.54,11.99L18.56,11.18L19.75,10.57L21.06,10.17L22.47,9.95L23.96,9.90L25.48,9.98L27.00,10.16L28.50,10.40L29.94,10.63L31.31,10.83L32.61,10.93L33.81,10.92L34.93,10.74L35.98,10.40L36.96,9.87L37.90,9.16L38.83,8.30L39.75,7.30L40.69,6.21L41.67,5.08L42.71,3.95L43.80,2.88L44.95,1.91L46.15,1.11L47.41,0.50L48.69,0.13L50.00,0.00Z"

export const M3_POLYGON_8_COG =
  "M50.00,0.00L51.31,0.07L52.61,0.29L53.88,0.66L55.13,1.18L56.34,1.88L57.47,2.81L58.50,4.16L59.02,7.56L59.86,8.95L60.73,9.95L61.62,10.77L62.52,11.48L63.43,12.09L64.35,12.61L65.31,13.04L66.29,13.41L67.32,13.70L68.39,13.91L69.52,14.05L70.73,14.09L72.06,14.00L73.63,13.62L76.40,11.58L78.08,11.35L79.55,11.49L80.89,11.85L82.15,12.36L83.31,13.01L84.38,13.77L85.36,14.64L86.23,15.62L86.99,16.69L87.64,17.85L88.15,19.11L88.51,20.45L88.65,21.92L88.42,23.60L86.38,26.37L86.00,27.94L85.91,29.27L85.95,30.48L86.09,31.61L86.30,32.68L86.59,33.71L86.96,34.69L87.39,35.65L87.91,36.57L88.52,37.48L89.23,38.38L90.05,39.27L91.05,40.14L92.44,40.98L95.84,41.50L97.19,42.53L98.12,43.66L98.82,44.87L99.34,46.12L99.71,47.39L99.93,48.69L100.00,50.00L99.93,51.31L99.71,52.61L99.34,53.88L98.82,55.13L98.12,56.34L97.19,57.47L95.84,58.50L92.44,59.02L91.05,59.86L90.05,60.73L89.23,61.62L88.52,62.52L87.91,63.43L87.39,64.35L86.96,65.31L86.59,66.29L86.30,67.32L86.09,68.39L85.95,69.52L85.91,70.73L86.00,72.06L86.38,73.63L88.42,76.40L88.65,78.08L88.51,79.55L88.15,80.89L87.64,82.15L86.99,83.31L86.23,84.38L85.36,85.36L84.38,86.23L83.31,86.99L82.15,87.64L80.89,88.15L79.55,88.51L78.08,88.65L76.40,88.42L73.63,86.38L72.06,86.00L70.73,85.91L69.52,85.95L68.39,86.09L67.32,86.30L66.29,86.59L65.31,86.96L64.35,87.39L63.43,87.91L62.52,88.52L61.62,89.23L60.73,90.05L59.86,91.05L59.02,92.44L58.50,95.84L57.47,97.19L56.34,98.12L55.13,98.82L53.88,99.34L52.61,99.71L51.31,99.93L50.00,100.00L48.69,99.93L47.39,99.71L46.12,99.34L44.87,98.82L43.66,98.12L42.53,97.19L41.50,95.84L40.98,92.44L40.14,91.05L39.27,90.05L38.38,89.23L37.48,88.52L36.57,87.91L35.65,87.39L34.69,86.96L33.71,86.59L32.68,86.30L31.61,86.09L30.48,85.95L29.27,85.91L27.94,86.00L26.37,86.38L23.60,88.42L21.92,88.65L20.45,88.51L19.11,88.15L17.85,87.64L16.69,86.99L15.62,86.23L14.64,85.36L13.77,84.38L13.01,83.31L12.36,82.15L11.85,80.89L11.49,79.55L11.35,78.08L11.58,76.40L13.62,73.63L14.00,72.06L14.09,70.73L14.05,69.52L13.91,68.39L13.70,67.32L13.41,66.29L13.04,65.31L12.61,64.35L12.09,63.43L11.48,62.52L10.77,61.62L9.95,60.73L8.95,59.86L7.56,59.02L4.16,58.50L2.81,57.47L1.88,56.34L1.18,55.13L0.66,53.88L0.29,52.61L0.07,51.31L0.00,50.00L0.07,48.69L0.29,47.39L0.66,46.12L1.18,44.87L1.88,43.66L2.81,42.53L4.16,41.50L7.56,40.98L8.95,40.14L9.95,39.27L10.77,38.38L11.48,37.48L12.09,36.57L12.61,35.65L13.04,34.69L13.41,33.71L13.70,32.68L13.91,31.61L14.05,30.48L14.09,29.27L14.00,27.94L13.62,26.37L11.58,23.60L11.35,21.92L11.49,20.45L11.85,19.11L12.36,17.85L13.01,16.69L13.77,15.62L14.64,14.64L15.62,13.77L16.69,13.01L17.85,12.36L19.11,11.85L20.45,11.49L21.92,11.35L23.60,11.58L26.37,13.62L27.94,14.00L29.27,14.09L30.48,14.05L31.61,13.91L32.68,13.70L33.71,13.41L34.69,13.04L35.65,12.61L36.57,12.09L37.48,11.48L38.38,10.77L39.27,9.95L40.14,8.95L40.98,7.56L41.50,4.16L42.53,2.81L43.66,1.88L44.87,1.18L46.12,0.66L47.39,0.29L48.69,0.07L50.00,0.00Z"

export const M3_POLYGON_HOURGLASS =
  "M50.00,0.00L51.31,0.12L52.60,0.46L53.85,1.02L55.07,1.79L56.22,2.75L57.31,3.87L58.32,5.13L59.25,6.50L60.10,7.94L60.87,9.43L61.57,10.93L62.21,12.41L62.81,13.83L63.37,15.18L63.91,16.42L64.45,17.54L65.01,18.53L65.61,19.36L66.27,20.03L67.00,20.56L67.82,20.93L68.73,21.16L69.75,21.26L70.88,21.26L72.12,21.17L73.47,21.01L74.92,20.82L76.45,20.63L78.05,20.44L79.70,20.30L81.37,20.23L83.05,20.24L84.70,20.36L86.29,20.61L87.81,20.99L89.21,21.51L90.49,22.17L91.61,22.98L92.55,23.93L93.30,25.00L93.85,26.19L94.20,27.48L94.34,28.85L94.28,30.28L94.03,31.76L93.60,33.26L93.01,34.77L92.30,36.26L91.47,37.72L90.57,39.13L89.62,40.49L88.66,41.78L87.73,43.01L86.84,44.17L86.03,45.26L85.33,46.29L84.76,47.26L84.34,48.20L84.09,49.11L84.00,50.00L84.09,50.89L84.34,51.80L84.76,52.74L85.33,53.71L86.03,54.74L86.84,55.83L87.73,56.99L88.66,58.22L89.62,59.51L90.57,60.87L91.47,62.28L92.30,63.74L93.01,65.23L93.60,66.74L94.03,68.24L94.28,69.72L94.34,71.15L94.20,72.52L93.85,73.81L93.30,75.00L92.55,76.07L91.61,77.02L90.49,77.83L89.21,78.49L87.81,79.01L86.29,79.39L84.70,79.64L83.05,79.76L81.37,79.77L79.70,79.70L78.05,79.56L76.45,79.37L74.92,79.18L73.47,78.99L72.12,78.83L70.88,78.74L69.75,78.74L68.73,78.84L67.82,79.07L67.00,79.44L66.27,79.97L65.61,80.64L65.01,81.47L64.45,82.46L63.91,83.58L63.37,84.82L62.81,86.17L62.21,87.59L61.57,89.07L60.87,90.57L60.10,92.06L59.25,93.50L58.32,94.87L57.31,96.13L56.22,97.25L55.07,98.21L53.85,98.98L52.60,99.54L51.31,99.88L50.00,100.00L48.69,99.88L47.40,99.54L46.15,98.98L44.93,98.21L43.78,97.25L42.69,96.13L41.68,94.87L40.75,93.50L39.90,92.06L39.13,90.57L38.43,89.07L37.79,87.59L37.19,86.17L36.63,84.82L36.09,83.58L35.55,82.46L34.99,81.47L34.39,80.64L33.73,79.97L33.00,79.44L32.18,79.07L31.27,78.84L30.25,78.74L29.12,78.74L27.88,78.83L26.53,78.99L25.08,79.18L23.55,79.37L21.95,79.56L20.30,79.70L18.63,79.77L16.95,79.76L15.30,79.64L13.71,79.39L12.19,79.01L10.79,78.49L9.51,77.83L8.39,77.02L7.45,76.07L6.70,75.00L6.15,73.81L5.80,72.52L5.66,71.15L5.72,69.72L5.97,68.24L6.40,66.74L6.99,65.23L7.70,63.74L8.53,62.28L9.43,60.87L10.38,59.51L11.34,58.22L12.27,56.99L13.16,55.83L13.97,54.74L14.67,53.71L15.24,52.74L15.66,51.80L15.91,50.89L16.00,50.00L15.91,49.11L15.66,48.20L15.24,47.26L14.67,46.29L13.97,45.26L13.16,44.17L12.27,43.01L11.34,41.78L10.38,40.49L9.43,39.13L8.53,37.72L7.70,36.26L6.99,34.77L6.40,33.26L5.97,31.76L5.72,30.28L5.66,28.85L5.80,27.48L6.15,26.19L6.70,25.00L7.45,23.93L8.39,22.98L9.51,22.17L10.79,21.51L12.19,20.99L13.71,20.61L15.30,20.36L16.95,20.24L18.63,20.23L20.30,20.30L21.95,20.44L23.55,20.63L25.08,20.82L26.53,21.01L27.88,21.17L29.12,21.26L30.25,21.26L31.27,21.16L32.18,20.93L33.00,20.56L33.73,20.03L34.39,19.36L34.99,18.53L35.55,17.54L36.09,16.42L36.63,15.18L37.19,13.83L37.79,12.41L38.43,10.93L39.13,9.43L39.90,7.94L40.75,6.50L41.68,5.13L42.69,3.87L43.78,2.75L44.93,1.79L46.15,1.02L47.40,0.46L48.69,0.12L50.00,0.00Z"

export function SectionBadge({
  number,
  polygon,
}: {
  number: string
  polygon: string
}) {
  return (
    <span className="relative grid size-14 shrink-0 place-items-center text-tertiary-container">
      <svg
        viewBox="0 0 100 100"
        aria-hidden="true"
        className="block absolute inset-0 size-full"
      >
        <path d={polygon} fill="currentColor" />
      </svg>
      <span className="relative font-mono text-sm font-bold text-on-tertiary-container tabular-nums">
        {number}
      </span>
    </span>
  )
}

/* -------------------------------------------------------------------------- */
/*  Hero Right Column: Binder IPC & Privilege Topology Schematic (No Images)  */
/* -------------------------------------------------------------------------- */

type LaunchPathKey = "adb" | "root" | "dhizuku"

const LAUNCH_PATHS: Record<
  LaunchPathKey,
  {
    label: string
    uidBadge: string
    starterOrigin: string
    serverIdentity: string
    providerAuthority: string
    capabilities: string[]
    limitation: string
  }
> = {
  adb: {
    label: "Wireless ADB",
    uidBadge: "UID 2000 (shell)",
    starterOrigin: "AdbPairingService + AdbStartWorker (TLS SPAKE2)",
    serverIdentity: "rikka.shizuku.server.ShizukuService (uid=2000)",
    providerAuthority: "moe.shizuku.privileged.api",
    capabilities: [
      "transactRemote() with shell permissions (WRITE_SECURE_SETTINGS, PACKAGE_USAGE_STATS)",
      "cmd connectivity set-chain3-enabled true (eBPF per-UID firewall)",
      "dpm set-device-owner com.hamondev.shevery/... (if no accounts present)",
    ],
    limitation:
      "Restricted to permissions listed in AOSP packages/Shell/AndroidManifest.xml. Cannot mount overlays or read arbitrary /data/data directories.",
  },
  root: {
    label: "Root (libsu)",
    uidBadge: "UID 0 (root)",
    starterOrigin: "com.topjohnwu.superuser.Shell -> starter binary",
    serverIdentity: "rikka.shizuku.server.ShizukuService (uid=0)",
    providerAuthority: "moe.shizuku.privileged.api",
    capabilities: [
      "Full UID 0 binder transactions & unrestricted IShizukuService.newProcess()",
      "Direct APK installation from private storage in StubManager",
      "Full access for ADB module action.sh and service.sh scripts",
    ],
    limitation:
      "Requires an existing su binary (Magisk, KernelSU, or APatch) to grant root to com.hamondev.shevery.",
  },
  dhizuku: {
    label: "Device Owner / Dhizuku",
    uidBadge: "DevicePolicyManager",
    starterOrigin: "SheveryDeviceAdminReceiver + DhizukuProvider",
    serverIdentity: "com.rosan.dhizuku.aidl.IDhizuku (v1 & v2)",
    providerAuthority: "com.hamondev.shevery.dhizuku.server",
    capabilities: [
      "DPM-assisted ADB toggle (setGlobalSetting adb_enabled / adb_wifi_enabled)",
      "Silent PackageInstaller sessions (--bypass-low-target-sdk-block on API 34+)",
      "Per-app delegation of 5 DPM scopes + Strict Whitelist Mode",
    ],
    limitation:
      "Requires Shevery to be running via Wireless Debugging or Root for full binder functionality.",
  },
}

export function HeroBinderTopology() {
  const [mode, setMode] = useState<LaunchPathKey>("adb")
  const active = LAUNCH_PATHS[mode]

  return (
    <figure className="relative mx-auto flex w-full min-w-0 max-w-lg flex-col items-center justify-center lg:col-span-5">
      <svg
        viewBox="0 0 100 100"
        aria-hidden="true"
        className="block absolute top-1/2 left-1/2 size-[110%] -translate-x-1/2 -translate-y-[52%] text-primary-container/60 pointer-events-none"
        style={{ animation: "m3-spin 40s linear infinite" }}
      >
        <path d={M3_POLYGON_12_LOBE} fill="currentColor" />
      </svg>
      <svg
        viewBox="0 0 100 100"
        aria-hidden="true"
        className="block absolute -bottom-4 -left-3 size-28 text-tertiary-container sm:size-36 pointer-events-none"
      >
        <path d={M3_POLYGON_4_PINCH} fill="currentColor" />
      </svg>

      <div className="relative w-full overflow-hidden rounded-2xl border border-outline-variant bg-surface-1 p-5 sm:p-6 shadow-2xl shadow-black/50">
        <div className="mb-4 flex items-center justify-between gap-2 border-b border-outline-variant/60 pb-3">
          <span className="font-mono text-xs tracking-wide text-on-surface-variant uppercase">
            Binder IPC Topology
          </span>
          <span className="rounded-full bg-primary-container px-3 py-1 font-mono text-xs font-semibold text-on-primary-container">
            {active.uidBadge}
          </span>
        </div>

        {/* Mode Switcher */}
        <div
          role="radiogroup"
          aria-label="Privilege launch mode"
          className="mb-4 flex flex-wrap gap-1.5"
        >
          {(Object.keys(LAUNCH_PATHS) as LaunchPathKey[]).map((key) => {
            const selected = key === mode
            return (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => setMode(key)}
                className={`h-9 px-3.5 text-xs font-medium transition-[border-radius,background-color,color] duration-300 ease-spring cursor-pointer ${
                  selected
                    ? "rounded-md bg-m3-primary text-on-primary"
                    : "rounded-[18px] bg-surface-3 text-on-surface hover:bg-surface-4"
                }`}
              >
                {LAUNCH_PATHS[key].label}
              </button>
            )
          })}
        </div>

        {/* Two-tone Parametric SVG Schematic of the IPC pipeline */}
        <div className="mb-4 rounded-xl bg-surface-2 p-3.5">
          <svg
            viewBox="0 0 360 88"
            className="w-full h-auto"
            role="img"
            aria-label={`IPC schematic for ${active.label}`}
          >
            {/* Structural reference lines (layer 1: muted) */}
            <line
              x1="108"
              y1="44"
              x2="136"
              y2="44"
              stroke="currentColor"
              strokeOpacity="0.28"
              strokeWidth="1.5"
              vectorEffect="non-scaling-stroke"
            />
            <line
              x1="232"
              y1="44"
              x2="260"
              y2="44"
              stroke="currentColor"
              strokeOpacity="0.28"
              strokeWidth="1.5"
              vectorEffect="non-scaling-stroke"
            />
            {/* Active focal vector (layer 2: primary/tertiary) */}
            <path
              d="M 108 44 L 134 44 M 129 39 L 135 44 L 129 49"
              stroke="var(--m3-primary)"
              strokeWidth="1.75"
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
              vectorEffect="non-scaling-stroke"
            />
            <path
              d="M 232 44 L 258 44 M 253 39 L 259 44 L 253 49"
              stroke="var(--m3-tertiary)"
              strokeWidth="1.75"
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
              vectorEffect="non-scaling-stroke"
            />

            {/* Node 1: Starter / Bootstrap */}
            <rect
              x="6"
              y="14"
              width="102"
              height="60"
              rx="12"
              fill="var(--m3-surface-container-high)"
              stroke="var(--m3-outline-variant)"
              strokeWidth="1.25"
            />
            <text
              x="57"
              y="36"
              textAnchor="middle"
              fill="var(--m3-on-surface-variant)"
              fontSize="9"
              fontFamily="var(--font-mono)"
            >
              BOOTSTRAP
            </text>
            <text
              x="57"
              y="53"
              textAnchor="middle"
              fill="var(--m3-on-surface)"
              fontSize="10"
              fontWeight="600"
              fontFamily="var(--font-mono)"
            >
              {mode === "adb" ? "libshizuku" : mode === "root" ? "su starter" : "dpm admin"}
            </text>

            {/* Node 2: Server / Binder Hub */}
            <rect
              x="136"
              y="10"
              width="96"
              height="68"
              rx="14"
              fill="var(--m3-primary-container)"
              stroke="var(--m3-primary)"
              strokeWidth="1.5"
            />
            <text
              x="184"
              y="34"
              textAnchor="middle"
              fill="var(--m3-on-primary-container)"
              fontSize="9"
              fontFamily="var(--font-mono)"
            >
              BINDER HUB
            </text>
            <text
              x="184"
              y="51"
              textAnchor="middle"
              fill="var(--m3-on-primary-container)"
              fontSize="10"
              fontWeight="700"
              fontFamily="var(--font-mono)"
            >
              {mode === "dhizuku" ? "IDhizuku" : "IShizuku"}
            </text>
            <text
              x="184"
              y="66"
              textAnchor="middle"
              fill="var(--m3-primary)"
              fontSize="8.5"
              fontFamily="var(--font-mono)"
            >
              5s Watchdog
            </text>

            {/* Node 3: System Server / Clients */}
            <rect
              x="260"
              y="14"
              width="94"
              height="60"
              rx="12"
              fill="var(--m3-tertiary-container)"
              stroke="var(--m3-tertiary)"
              strokeWidth="1.25"
            />
            <text
              x="307"
              y="36"
              textAnchor="middle"
              fill="var(--m3-on-tertiary-container)"
              fontSize="9"
              fontFamily="var(--font-mono)"
            >
              TARGET
            </text>
            <text
              x="307"
              y="53"
              textAnchor="middle"
              fill="var(--m3-on-tertiary-container)"
              fontSize="10"
              fontWeight="600"
              fontFamily="var(--font-mono)"
            >
              system_server
            </text>
          </svg>
        </div>

        <dl className="flex flex-col gap-2 text-xs">
          <div className="flex flex-col gap-0.5 rounded-lg bg-surface-2 px-3.5 py-2.5">
            <dt className="text-[11px] uppercase tracking-wide text-on-surface-variant">
              Entry point
            </dt>
            <dd className="font-mono text-on-surface break-words">
              {active.starterOrigin}
            </dd>
          </div>
          <div className="flex flex-col gap-0.5 rounded-lg bg-surface-2 px-3.5 py-2.5">
            <dt className="text-[11px] uppercase tracking-wide text-on-surface-variant">
              Provider authority
            </dt>
            <dd className="font-mono text-m3-primary break-all">
              {active.providerAuthority}
            </dd>
          </div>
        </dl>

        <ul className="mt-3 flex flex-col gap-1.5 text-xs leading-relaxed text-on-surface-variant">
          {active.capabilities.map((cap) => (
            <li key={cap} className="flex gap-2">
              <span className="font-mono text-m3-primary shrink-0">+</span>
              <span className="break-words">{cap}</span>
            </li>
          ))}
        </ul>

        <p className="mt-3 rounded-lg bg-error-container px-3.5 py-2.5 text-xs leading-relaxed text-on-error-container">
          {active.limitation}
        </p>
      </div>

      <figcaption className="relative mt-4 text-center text-sm text-on-surface-variant">
        Interactive schematic of the three privilege paths implemented in{" "}
        <code className="font-mono text-on-surface">moe.shizuku.manager</code> and{" "}
        <code className="font-mono text-on-surface">rikka.shizuku.server</code>.
      </figcaption>
    </figure>
  )
}

/* -------------------------------------------------------------------------- */
/*  Section 02: Stealth APK Byte-Swap & Android17Compat Inspector             */
/* -------------------------------------------------------------------------- */

type InspectorMode = "stealth-swap" | "compat-stubs" | "android17-compat"

function generateRandomStealthPkg(): string {
  const letters = "abcdefghijklmnopqrstuvwxyz"
  const alnum = "abcdefghijklmnopqrstuvwxyz0123456789"
  const seg = () => {
    let s = letters[Math.floor(Math.random() * letters.length)]
    for (let i = 0; i < 7; i++) {
      s += alnum[Math.floor(Math.random() * alnum.length)]
    }
    return s
  }
  return `com.${seg()}.${seg()}`
}

export function StealthAndCompatInspector() {
  const [mode, setMode] = useState<InspectorMode>("stealth-swap")
  const [samplePkg, setSamplePkg] = useState("com.k8m2x9qp.r4v7n1tw")

  return (
    <div className="overflow-hidden rounded-2xl bg-surface-2">
      <div
        role="radiogroup"
        aria-label="Mechanism inspector"
        className="flex flex-wrap items-center justify-between gap-2 p-4 sm:p-6"
      >
        <div className="flex flex-wrap gap-2">
          {[
            {
              id: "stealth-swap" as const,
              label: "Stealth byte-swap (21 chars)",
            },
            {
              id: "compat-stubs" as const,
              label: "StubManager 4-stage install",
            },
            {
              id: "android17-compat" as const,
              label: "Android17Compat fallback flaw",
            },
          ].map((tab) => {
            const checked = mode === tab.id
            return (
              <button
                key={tab.id}
                type="button"
                role="radio"
                aria-checked={checked}
                onClick={() => setMode(tab.id)}
                className={`h-12 px-5 text-sm font-medium transition-[border-radius,background-color,color] duration-300 ease-spring cursor-pointer ${
                  checked
                    ? "rounded-md bg-m3-primary text-on-primary"
                    : "rounded-[24px] bg-surface-3 text-on-surface hover:bg-surface-4"
                }`}
              >
                {tab.label}
              </button>
            )
          })}
        </div>

        {mode === "stealth-swap" && (
          <button
            type="button"
            onClick={() => setSamplePkg(generateRandomStealthPkg())}
            className="h-10 rounded-full border border-outline px-4 font-mono text-xs font-medium text-on-surface transition-colors hover:bg-surface-3 cursor-pointer"
          >
            Roll randomPackage()
          </button>
        )}
      </div>

      {mode === "stealth-swap" && (
        <div className="grid grid-cols-1 gap-px bg-outline-variant/40 lg:grid-cols-12">
          <div className="min-w-0 bg-surface-1 p-4 sm:p-6 lg:col-span-7">
            <p className="mb-3 text-sm text-on-surface-variant">
              StealthApkGenerator.kt — fixed 21-char template replaced in ASCII &amp; UTF-16LE
            </p>
            <pre className="overflow-x-auto font-mono text-sm leading-relaxed sm:text-base">
              <code>
                <span className="text-on-surface-variant">const val </span>
                <span className="text-m3-primary">TEMPLATE_PACKAGE</span> ={" "}
                <span className="rounded-sm bg-error-container px-1 text-on-error-container">
                  &quot;com.stealthp.lacehold&quot;
                </span>{" "}
                <span className="text-on-surface-variant">// 21 bytes</span>
                {"\n"}
                <span className="text-on-surface-variant">val </span>
                <span className="text-m3-primary">generatedPackage</span> ={" "}
                <span className="rounded-sm bg-primary-container px-1 text-on-primary-container">
                  &quot;{samplePkg}&quot;
                </span>{" "}
                <span className="text-on-surface-variant">
                  // {samplePkg.length} bytes (com.&lt;8&gt;.&lt;8&gt;)
                </span>
                {"\n\n"}
                <span className="text-on-surface-variant">
                  // Rewritten across AndroidManifest.xml, resources.arsc, classes*.dex:
                </span>
                {"\n"}
                replaceBytes(data, oldPkgAscii, newPkgAscii){"\n"}
                replaceBytes(data, oldPkgUtf16Le, newPkgUtf16Le){"\n"}
                patchDexHeader(dexBytes){" "}
                <span className="text-on-surface-variant">
                  // SHA-1 [12..32] + Adler32 [8..12]
                </span>
              </code>
            </pre>
          </div>
          <div className="flex flex-col gap-4 bg-surface-1 p-4 sm:p-6 lg:col-span-5">
            <p className="text-sm text-on-surface-variant">Generator output</p>
            <p
              aria-live="polite"
              className="w-fit rounded-full bg-primary-container px-4 py-2 font-mono text-sm font-bold text-on-primary-container"
            >
              MediaStore.Downloads · v1 JAR signed
            </p>
            <p className="leading-relaxed text-on-surface-variant text-pretty">
              Because neither the package ID (<code className="font-mono text-on-surface">21</code>{" "}
              chars) nor the space-padded launcher label (<code className="font-mono text-on-surface">14</code>{" "}
              chars, <code className="font-mono text-on-surface">&quot;System Toolkit&quot;</code>)
              changes byte length, string-pool offsets in{" "}
              <code className="font-mono text-on-surface">resources.arsc</code> stay valid without
              parsing AXML.
            </p>
          </div>
        </div>
      )}

      {mode === "compat-stubs" && (
        <div className="grid grid-cols-1 gap-px bg-outline-variant/40 lg:grid-cols-12">
          <div className="min-w-0 bg-surface-1 p-4 sm:p-6 lg:col-span-7">
            <p className="mb-3 text-sm text-on-surface-variant">
              StubManager.kt — multi-channel install order for shevery-stub.apk &amp;
              shevery-dhizuku-stub.apk
            </p>
            <pre className="overflow-x-auto font-mono text-sm leading-relaxed sm:text-base">
              <code>
                <span className="text-on-surface-variant">// 1. Device / Profile Owner session</span>
                {"\n"}
                PackageInstaller.SessionParams(MODE_FULL_INSTALL)
                {"\n"}
                {"  "}
                <span className="rounded-sm bg-primary-container px-1 text-on-primary-container">
                  --bypass-low-target-sdk-block
                </span>{" "}
                <span className="text-on-surface-variant">// API 34+</span>
                {"\n\n"}
                <span className="text-on-surface-variant">// 2. Active Shizuku binder</span>
                {"\n"}
                pm install -r -d -t -g /data/local/tmp/shevery-stub.apk
                {"\n\n"}
                <span className="text-on-surface-variant">// 3. Root shell (libsu)  4. Local ADB (127.0.0.1)</span>
              </code>
            </pre>
          </div>
          <div className="flex flex-col gap-4 bg-surface-1 p-4 sm:p-6 lg:col-span-5">
            <p className="text-sm text-on-surface-variant">Stub package IDs</p>
            <p
              aria-live="polite"
              className="w-fit rounded-full bg-tertiary-container px-4 py-2 font-mono text-sm font-bold text-on-tertiary-container"
            >
              moe.shizuku.privileged.api · com.rosan.dhizuku
            </p>
            <p className="leading-relaxed text-on-surface-variant text-pretty">
              Some client apps call <code className="font-mono text-on-surface">getPackageInfo</code>{" "}
              for the hardcoded upstream package name before binding. Installing the stub satisfies
              that check and forwards permission and binder intents to Shevery.
            </p>
          </div>
        </div>
      )}

      {mode === "android17-compat" && (
        <div className="grid grid-cols-1 gap-px bg-outline-variant/40 lg:grid-cols-12">
          <div className="min-w-0 bg-surface-1 p-4 sm:p-6 lg:col-span-7">
            <p className="mb-3 text-sm text-on-surface-variant">
              server/src/main/java/rikka/shizuku/server/util/Android17Compat.java (lines 121 &amp; 253)
            </p>
            <pre className="overflow-x-auto font-mono text-sm leading-relaxed sm:text-base">
              <code>
                <span className="text-on-surface-variant">// Fallback in Android17Compat.checkPermission:</span>
                {"\n"}
                invokeDynamic(pm, sCheckPermissionMethod,{"\n"}
                {"    "}
                <span className="rounded-sm bg-error-container px-1 text-on-error-container">
                  packageName
                </span>
                ,{" "}
                <span className="text-on-surface-variant">
                  // 1st arg: platform expects permissionName
                </span>
                {"\n"}
                {"    "}
                <span className="rounded-sm bg-error-container px-1 text-on-error-container">
                  permissionName
                </span>
                ,{" "}
                <span className="text-on-surface-variant">
                  // 2nd arg: platform expects packageName
                </span>
                {"\n"}
                {"    "}
                <span className="rounded-sm bg-error-container px-1 text-on-error-container">
                  &quot;default&quot;
                </span>
                ,{" "}
                <span className="text-on-surface-variant">
                  // injected by invokeDynamic (platform uses &quot;default:0&quot;)
                </span>
                {"\n"}
                {"    "}
                <span className="text-on-surface">userId</span>
                {"\n"}
                );
              </code>
            </pre>
          </div>
          <div className="flex flex-col gap-4 bg-surface-1 p-4 sm:p-6 lg:col-span-5">
            <p className="text-sm text-on-surface-variant">Fallback result</p>
            <p
              aria-live="polite"
              className="w-fit rounded-full bg-error-container px-4 py-2 font-mono text-sm font-bold text-on-error-container"
            >
              PERMISSION_DENIED
            </p>
            <p className="leading-relaxed text-on-surface-variant text-pretty">
              In Shevery&apos;s current <code className="font-mono text-on-surface">Android17Compat.java</code>,{" "}
              <code className="font-mono text-on-surface">grantRuntimePermission</code> and{" "}
              <code className="font-mono text-on-surface">revokeRuntimePermission</code> pass{" "}
              <code className="font-mono text-on-surface">(packageName, permissionName)</code> in the
              right order, but <code className="font-mono text-on-surface">checkPermission</code>{" "}
              also passes <code className="font-mono text-on-surface">packageName</code> first and
              injects <code className="font-mono text-on-surface">&quot;default&quot;</code> instead
              of <code className="font-mono text-on-surface">&quot;default:0&quot;</code>.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/*  Section 03: Server Watchdog & Rebind Backoff Simulator                    */
/* -------------------------------------------------------------------------- */

// Computed directly from server/src/main/java/rikka/shizuku/server/Watchdog.java:
// First rebind is posted with 2000L ms delay (line 168), then on failure
// Math.min(5000L * (1L << attempts), 60_000L) for attempts 1..9 (line 205).
const REBIND_DELAYS_SEC = [2, 5, 10, 20, 40, 60, 60, 60, 60, 60]

function formatDuration(sec: number): string {
  if (sec < 60) return `${sec}s`
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}m ${s.toString().padStart(2, "0")}s`
}

export function WatchdogBackoffSimulator() {
  const [recoveryAttempt, setRecoveryAttempt] = useState<number>(4)

  const cumulativeSeconds = REBIND_DELAYS_SEC.slice(0, recoveryAttempt).reduce(
    (acc, v) => acc + v,
    0
  )

  return (
    <div className="rounded-2xl bg-surface-2 p-4 sm:p-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-3">
          <label
            htmlFor="watchdog-attempt-slider"
            className="text-sm font-medium text-on-surface-variant"
          >
            System service (<code className="font-mono text-on-surface">package</code> /{" "}
            <code className="font-mono text-on-surface">activity</code> /{" "}
            <code className="font-mono text-on-surface">user</code> /{" "}
            <code className="font-mono text-on-surface">appops</code>) died. Rebinds on attempt:
          </label>
          <input
            id="watchdog-attempt-slider"
            type="range"
            min={1}
            max={11}
            value={recoveryAttempt}
            onChange={(e) => setRecoveryAttempt(Number(e.target.value))}
            aria-valuetext={
              recoveryAttempt <= 10 ? `attempt ${recoveryAttempt}` : "all 10 attempts failed"
            }
            className="h-2 w-full max-w-md cursor-pointer accent-[var(--m3-tertiary)] sm:w-80"
          />
        </div>

        <p aria-live="polite" className="text-right">
          {recoveryAttempt <= 10 ? (
            <>
              <span className="block text-4xl font-bold tabular-nums text-tertiary">
                #{recoveryAttempt}
              </span>
              <span className="text-sm text-on-surface-variant">
                recovered after {cumulativeSeconds}s
              </span>
            </>
          ) : (
            <>
              <span className="block text-4xl font-bold tabular-nums text-error">
                Give up
              </span>
              <span className="text-sm text-on-surface-variant">
                after 10 attempts (377s total)
              </span>
            </>
          )}
        </p>
      </div>

      <ol
        className="flex h-48 items-end gap-1.5 sm:gap-2"
        aria-label="Delay before each rebind attempt in Watchdog.java"
      >
        {REBIND_DELAYS_SEC.map((sec, idx) => {
          const attemptNum = idx + 1
          const isFailed = attemptNum < recoveryAttempt
          const isSuccess = attemptNum === recoveryAttempt && recoveryAttempt <= 10
          const heightPct = Math.max(6, (sec / 60) * 100)

          let barClass = "bg-surface-4 opacity-40"
          let statusLabel = "skipped"
          if (isFailed) {
            barClass = "bg-error-container"
            statusLabel = "failed"
          } else if (isSuccess) {
            barClass = "bg-tertiary"
            statusLabel = "ok"
          }

          return (
            <li
              key={attemptNum}
              className="flex h-full flex-1 flex-col items-center justify-end gap-2"
            >
              <span className="font-mono text-[11px] text-on-surface-variant tabular-nums sm:text-xs">
                {formatDuration(sec)}
              </span>
              <span
                className={`w-full rounded-t-lg rounded-b-sm transition-[background-color,height,opacity] duration-500 ease-spring-soft ${barClass}`}
                style={{ height: `${heightPct}%` }}
              />
              <span className="font-mono text-xs text-on-surface tabular-nums">
                <span className="sr-only">
                  Attempt {attemptNum}, {statusLabel}.{" "}
                </span>
                <span aria-hidden="true">{attemptNum}</span>
              </span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
