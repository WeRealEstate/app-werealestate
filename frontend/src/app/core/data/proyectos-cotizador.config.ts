export type ProjectId = 'samai' | 'nanuu';

export interface FinancingPlan {
  id: string;
  name: string;
  description?: string;
  months: number;
  downPaymentPercentage?: number;
  interestPercentage: number;
  type: 'financing' | 'cash';
}

export interface ProjectConfig {
  id: ProjectId;
  name: string;
  shortName: string;
  location: string;
  pricePerM2: number;
  minimumArea: number;

  financingPlans: FinancingPlan[];

  logo?: string;
  image?: string;
}

export const PROJECTS_CONFIG: Record<ProjectId, ProjectConfig> = {
  samai: {
    id: 'samai',

    name: 'SAMAI Campestre',

    shortName: 'SAMAI',

    location: 'San José La Ciénega, Pochutla, Oaxaca',

    pricePerM2: 800,

    minimumArea: 200,

    logo: 'assets/images/samai-logo.png',

    image: 'assets/images/samai-cover.jpg',

    financingPlans: [
      {
        id: 'samai-60-msi',
        name: '60 MSI sin intereses',
        description: 'Sin enganche',
        months: 60,
        downPaymentPercentage: 0,
        interestPercentage: 0,
        type: 'financing'
      },

      {
        id: 'samai-downpayment',
        name: 'Con enganche',
        description: 'Hasta 60 meses',
        months: 60,
        downPaymentPercentage: 10,
        interestPercentage: 0,
        type: 'financing'
      },

      {
        id: 'samai-cash',
        name: 'Contado',
        description: 'Precio especial',
        months: 1,
        downPaymentPercentage: 100,
        interestPercentage: 0,
        type: 'cash'
      }
    ]
  },

  nanuu: {
    id: 'nanuu',

    name: 'Aldea Nanuu',

    shortName: 'NANUU',

    location: 'Cuatunalco, Santa María Huatulco, Oaxaca',

    pricePerM2: 3700,

    minimumArea: 200,

    logo: 'assets/images/nanuu-logo.png',

    image: 'assets/images/nanuu-cover.jpg',

    financingPlans: [
      {
        id: 'nanuu-24',
        name: '24 MSI',
        description: 'Sin intereses',
        months: 24,
        //downPaymentPercentage: 20,
        interestPercentage: 0,
        type: 'financing'
      },

      {
        id: 'nanuu-36',
        name: '36 meses',
        description: '5% anual',
        months: 36,
        //downPaymentPercentage: 20,
        interestPercentage: 5,
        type: 'financing'
      },

      {
        id: 'nanuu-48',
        name: '48 meses',
        description: '10% anual',
        months: 48,
        //downPaymentPercentage: 20,
        interestPercentage: 10,
        type: 'financing'
      },

      {
        id: 'nanuu-60',
        name: '60 meses',
        description: '20% anual',
        months: 60,
        //downPaymentPercentage: 20,
        interestPercentage: 20,
        type: 'financing'
      },

      {
        id: 'nanuu-cash',
        name: 'Contado',
        description: 'Pago de contado',
        months: 1,
        //downPaymentPercentage: 100,
        interestPercentage: 0,
        type: 'cash'
      }
    ]
  }
};